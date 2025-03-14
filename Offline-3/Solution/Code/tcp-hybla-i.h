/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/*
 * This is a tweaked version of TCP Hybla named TcpHyblaI.
 * It introduces:
 *  - A smoothed RTT (sRtt)-based rho calculation.
 *  - Adjustments of cwnd increment based on in_flight_bytes ratio, RTT variability, and outstanding data.
 *
 * The main differences from the original TcpHybla:
 * - Uses a smoothed RTT instead of minRtt alone for recalculating rho.
 * - Modulates cwnd growth based on a simple RTO estimate derived from srtt.
 * - Scales cwnd increments based on outstanding bytes and inflight ratio.
 */

#ifndef TCPHYBLAI_H
#define TCPHYBLAI_H

#include "tcp-hybla.h"
#include "ns3/traced-value.h"
#include "ns3/nstime.h"

namespace ns3
{

class TcpSocketState;

/**
 * \ingroup congestionOps
 *
 * \brief A modified TCP Hybla variant (TcpHyblaI) with improved parameter handling.
 *
 * This variant:
 * - Uses a smoothed RTT (sRtt) to compute a stable rho parameter, reducing abrupt changes.
 * - Adjusts congestion window increments based on network conditions: in-flight ratio, a simple RTO estimate,
 *   and outstanding data.
 */
class TcpHyblaI : public TcpHybla
{
public:
    /**
     * \brief Get the type ID.
     * \return the object TypeId
     */
    static TypeId GetTypeId();

    TcpHyblaI();
    TcpHyblaI(const TcpHyblaI& sock);
    ~TcpHyblaI() override;

    // Inherited
    void PktsAcked(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked, const Time& rtt) override;
    Ptr<TcpCongestionOps> Fork() override;
    std::string GetName() const override;

protected:
    uint32_t SlowStart(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked) override;
    void CongestionAvoidance(Ptr<TcpSocketState> tcb, uint32_t segmentsAcked) override;

private:
    // New parameters
    Time m_sRtt;             //!< Smoothed RTT
    double m_alpha;          //!< Smoothing factor for sRtt
    double m_inFlightThresh; //!< Threshold for in_flight to cwnd ratio
    double m_rtoScalingFactor; //!< Factor to scale increments if RTO is large vs sRtt

    // Re-implemented parameters from Hybla since we can't access parent's private members
    Time m_rRtt;     //!< Reference RTT for HyblaI
    double m_rho;     //!< Rho parameter
    double m_cWndCnt; //!< cWnd integer-to-float counter

private:
    /**
     * \brief Recalculate algorithm parameters with smoothed RTT.
     * \param tcb the socket state.
     */
    void HyblaIRecalcParam(const Ptr<TcpSocketState>& tcb);

    /**
     * \brief Compute increment scaling factor based on network conditions.
     * \param tcb the socket state.
     * \return scaling factor (<= 1.0) to modulate cwnd increment.
     */
    double ComputeScalingFactor(const Ptr<TcpSocketState>& tcb);
};

} // namespace ns3

#endif // TCPHYBLAI_H
