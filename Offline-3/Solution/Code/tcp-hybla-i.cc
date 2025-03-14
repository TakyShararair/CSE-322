#include "tcp-hybla-i.h"
#include "tcp-socket-state.h"
#include "ns3/log.h"
#include <cmath>     // for pow
#include <algorithm> // for std::max

/*
In the original TCP Hybla, the parameter rho is recalculated based solely on
the minimum observed RTT (m_minRtt) compared to a reference RTT (m_rRtt). 
This approach can lead to somewhat abrupt changes in the congestion window growth rate
when the minimum RTT changes. Also, the original Hybla does not directly consider other
parameters like in_flight_bytes, next_tx, next_rx, or a smoothed RTT estimate in its control decisions.


Proposed Tweak:

Smoothed RTT-based Rho Calculation:
Instead of using the instantaneous m_minRtt, we introduce a smoothed RTT (m_sRtt)
 that blends the minimum RTT with the current RTT measurements. This smoothing can 
 reduce abrupt changes in rho, leading to more stable congestion window adjustments over time.

Dynamic Scaling Based on In-Flight Data:
Adjust the cwnd increment in both slow start and congestion avoidance by considering
 the ratio of in_flight_bytes to the current congestion window. If in_flight_bytes
  are consistently high relative to cwnd, we apply a slight reduction factor to the cwnd increment.
   This prevents overshoot in scenarios where the network is already quite utilized.

Enhanced Slow Start and Congestion Avoidance:
During slow start, we still follow the Hybla growth rule, but we scale down the
 exponential factor slightly if in_flight_bytes / cwnd is above a threshold (e.g., 0.8).
  During congestion avoidance, we also incorporate this ratio to prevent overly aggressive 
  increments when the network is nearing capacity.

Incorporation of next_tx and next_rx:
Although these fields typically reflect internal TCP state (like the next sequence number 
to transmit and the next expected ACK), we can demonstrate their awareness by logging or slightly
 adjusting behavior if the gap between next_tx and next_rx grows large. A large gap may indicate
  delayed ACKs or network sluggishness; thus we reduce the increment factor slightly during congestion avoidance.

RTO Consideration:
If the current RTO is large compared to the smoothed RTT, this might indicate a
 more uncertain network environment. We can use the RTO ratio (RTO / sRtt) as another scaling factor,
  reducing cwnd growth if the RTO is disproportionately large.

*/

namespace ns3
{

NS_LOG_COMPONENT_DEFINE("TcpHyblaI");
NS_OBJECT_ENSURE_REGISTERED(TcpHyblaI);

TypeId
TcpHyblaI::GetTypeId()
{
    static TypeId tid = TypeId("ns3::TcpHyblaI")
                            .SetParent<TcpHybla>()
                            .AddConstructor<TcpHyblaI>()
                            .SetGroupName("Internet")
                            .AddAttribute("Alpha",
                                          "Smoothing factor for sRtt (0 < alpha <= 1)",
                                          DoubleValue(0.9),
                                          MakeDoubleAccessor(&TcpHyblaI::m_alpha),
                                          MakeDoubleChecker<double>(0.0, 1.0))
                            .AddAttribute("InFlightThreshold",
                                          "Threshold ratio for in_flight_bytes/cwnd beyond which increments are slowed",
                                          DoubleValue(0.8),
                                          MakeDoubleAccessor(&TcpHyblaI::m_inFlightThresh),
                                          MakeDoubleChecker<double>())
                            .AddAttribute("RtoScalingFactor",
                                          "Factor to reduce increments if RTO/sRtt is large",
                                          DoubleValue(1.0),
                                          MakeDoubleAccessor(&TcpHyblaI::m_rtoScalingFactor),
                                          MakeDoubleChecker<double>(0.0));
    // Note: We do not re-register "RRTT" because it is already registered in TcpHybla.
    return tid;
}

TcpHyblaI::TcpHyblaI()
    : TcpHybla(),
      m_sRtt(Seconds(0)),
      m_alpha(0.9),
      m_inFlightThresh(0.8),
      m_rtoScalingFactor(1.0),
      m_rRtt(MilliSeconds(50)),  // We can still set this default if needed
      m_rho(1.0),
      m_cWndCnt(0.0)
{
    NS_LOG_FUNCTION(this);
}

TcpHyblaI::TcpHyblaI(const TcpHyblaI& sock)
    : TcpHybla(sock),
      m_sRtt(sock.m_sRtt),
      m_alpha(sock.m_alpha),
      m_inFlightThresh(sock.m_inFlightThresh),
      m_rtoScalingFactor(sock.m_rtoScalingFactor),
      m_rRtt(sock.m_rRtt),
      m_rho(sock.m_rho),
      m_cWndCnt(sock.m_cWndCnt)
{
    NS_LOG_FUNCTION(this);
}

TcpHyblaI::~TcpHyblaI()
{
    NS_LOG_FUNCTION(this);
}

std::string
TcpHyblaI::GetName() const
{
    return "TcpHyblaI";
}

void
TcpHyblaI::PktsAcked(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked, const Time& rtt)
{
    NS_LOG_FUNCTION(this << tcb << segmentsAcked << rtt);

    if (m_sRtt == Seconds(0))
    {
        m_sRtt = rtt;
    }
    else
    {
        m_sRtt = (m_sRtt * m_alpha) + (rtt * (1.0 - m_alpha));
    }

    if (rtt == tcb->m_minRtt)
    {
        HyblaIRecalcParam(tcb);
        NS_LOG_DEBUG("min RTT seen: " << rtt);
    }
}

void
TcpHyblaI::HyblaIRecalcParam(const Ptr<TcpSocketState>& tcb)
{
    NS_LOG_FUNCTION(this);

    Time effectiveRtt = (m_sRtt.IsZero()) ? tcb->m_minRtt : m_sRtt;
    double candidateRho =
        (double)effectiveRtt.GetMilliSeconds() / (double)m_rRtt.GetMilliSeconds();
    m_rho = std::max(candidateRho, 1.0);

    NS_ASSERT(m_rho > 0.0);
    NS_LOG_DEBUG("Recalculated rho using sRtt: rho=" << m_rho);
}

double
TcpHyblaI::ComputeScalingFactor(const Ptr<TcpSocketState>& tcb)
{
    double cwndInBytes = (double)tcb->m_cWnd;
    double inFlight = (double)tcb->m_bytesInFlight;

    double inflightRatio = (cwndInBytes > 0) ? inFlight / cwndInBytes : 0.0;

    Time srttTime = tcb->m_srtt;
    double srttSeconds = srttTime.GetSeconds();

    double computedRto = srttSeconds * 2.0;

    double rtoRatio = (m_sRtt.IsZero()) ? 1.0 : (computedRto / m_sRtt.GetSeconds());
    rtoRatio = std::max(rtoRatio, 1.0);

    uint32_t outstanding = tcb->m_nextTxSequence - tcb->m_lastAckedSeq;
    double outstandingFactor = 1.0;
    if (outstanding > tcb->GetCwndInSegments() * 2)
    {
        outstandingFactor = 0.9;
    }

    double inflightFactor = 1.0;
    if (inflightRatio > m_inFlightThresh)
    {
        inflightFactor = 1.0 - ((inflightRatio - m_inFlightThresh) * 0.5);
        inflightFactor = std::max(inflightFactor, 0.5);
    }

    double rtoFactor = 1.0 / (1.0 + (rtoRatio - 1.0) * m_rtoScalingFactor);

    double finalFactor = inflightFactor * rtoFactor * outstandingFactor;
    finalFactor = std::max(finalFactor, 0.5);
    return finalFactor;
}

uint32_t
TcpHyblaI::SlowStart(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked)
{
    NS_LOG_FUNCTION(this << tcb << segmentsAcked);

    NS_ASSERT(tcb->m_cWnd <= tcb->m_ssThresh);

    if (segmentsAcked >= 1)
    {
        double increment = std::pow(2, m_rho) - 1.0;

        double scale = ComputeScalingFactor(tcb);
        increment *= scale;

        uint32_t incr = static_cast<uint32_t>(increment * tcb->m_segmentSize);

        uint32_t oldCwnd = tcb->m_cWnd;
        tcb->m_cWnd = std::min(tcb->m_cWnd + incr, tcb->m_ssThresh);

        NS_LOG_INFO("In SlowStart (HyblaI), updated cwnd from " << oldCwnd << " to " << tcb->m_cWnd
                                                                << " ssthresh " << tcb->m_ssThresh
                                                                << " increment=" << (increment * tcb->m_segmentSize)
                                                                << " scale=" << scale);

        return segmentsAcked - 1;
    }

    return 0;
}

void
TcpHyblaI::CongestionAvoidance(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked)
{
    NS_LOG_FUNCTION(this << tcb << segmentsAcked);

    while (segmentsAcked > 0)
    {
        uint32_t segCwnd = tcb->GetCwndInSegments();
        double increment = (std::pow(m_rho, 2) / static_cast<double>(segCwnd));

        m_cWndCnt += increment;
        segmentsAcked -= 1;
    }

    if (m_cWndCnt >= 1.0)
    {
        uint32_t inc = static_cast<uint32_t>(m_cWndCnt);
        m_cWndCnt -= inc;

        double scale = ComputeScalingFactor(tcb);
        uint32_t scaledInc = (uint32_t)((double)inc * scale * (double)tcb->m_segmentSize);

        uint32_t oldCwnd = tcb->m_cWnd;
        tcb->m_cWnd += scaledInc;

        NS_LOG_INFO("In CongAvoid (HyblaI), updated cwnd from " << oldCwnd << " to " << tcb->m_cWnd
                                                                << " ssthresh " << tcb->m_ssThresh
                                                                << " increment=" << scaledInc
                                                                << " scale=" << scale);
    }
}

Ptr<TcpCongestionOps>
TcpHyblaI::Fork()
{
    return CopyObject<TcpHyblaI>(this);
}

} // namespace ns3
