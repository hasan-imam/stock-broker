#!/bin/bash
# Run the broker server for a particular exchange

# arguments to OnlineBroker
# $1 = hostname of BrokerLookupServer
# $2 = port where BrokerLookupServer is listening
# $3 = port where I will be listening
# $4 = my name ("nasdaq" or "tse")

java OnlineBroker $1 $2 $3 $4






