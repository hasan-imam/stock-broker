#!/bin/bash
# Run the lookup/name server. This needs to be put online first for the other parts of the service to startup

# arguments to BrokerLookupServer
# $1 = port # of where name server will be listening

java BrokerLookupServer $1




