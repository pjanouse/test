GREP=`grep "Failures: 0" log`
echo $GREP
	if [ ! -z "($GREP)" ] 
        then
		echo "Breaking loop - there is failure"
		break       	   #Abandon the loop.
	fi
