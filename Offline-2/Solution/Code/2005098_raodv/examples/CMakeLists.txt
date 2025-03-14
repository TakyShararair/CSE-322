build_lib_example(
  NAME raodv-example
  SOURCE_FILES raodv-example.cc
  LIBRARIES_TO_LINK
    ${libwifi}
    ${libinternet}
    ${libaodv}
    ${libinternet-apps}
)
