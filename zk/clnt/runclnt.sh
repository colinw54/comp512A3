#!/bin/bash

if [[ -z "$ZOOBINDIR" ]]
then
	echo "Error!! ZOOBINDIR is not set" 1>&2
	exit 1
fi

. $ZOOBINDIR/zkEnv.sh

# 	Replace with your server names and client ports.
export ZKSERVER=tr-open-10.cs.mcgill.ca:21820,tr-open-12.cs.mcgill.ca:21820,tr-open-14.cs.mcgill.ca:21820

java -cp $CLASSPATH:../task:.: DistClient "$@"
