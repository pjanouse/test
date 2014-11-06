# 1st parameter is process id of EAP server
# 2nd parameter is prefix for csv (for example: server1 will create server1-out-memory.csv)

#!/bin/bash

pid="$1"

file_name_memory_csv=$2"-out_memory.csv"
file_name_cpu_csv=$2"-out_cpu.csv"

echo "Pid is: $1, file_name_memory_csv = $file_name_memory_csv, file_name_cpu_csv = $file_name_cpu_csv"

fin=false
echo 'SOAK test Memory usage, MB' | tee "$file_name_memory_csv"
echo 'SOAK test CPU usage, %' | tee "$file_name_memory_csv"
echo "Timestamp; PS Young Generation Eden Space; From Space; To Space; PS Old Generation capacity; PS Perm Generation capacity;" >> "$file_name_memory_csv"
v=`vmstat | head -2| tail -1 | sed  "s/\([a-z][a-z]*\) */\1;/13g" | sed "s/\([a-z][a-z]* \)//g"`
echo "Timestamp; $v" >> "$file_name_memory_csv"
while [ $fin != true ]
do
ccc=`jmap -heap $pid | grep "used" | grep "(" | sed "s/.*(\(.*\)MB)/\1;/" | tr -d "\n"`
if [[ "$ccc" == "" ]]; then
jps
sleep 10s
ccc=`jmap -heap $pid | grep "used" | grep "(" | sed "s/.*(\(.*\)MB)/\1;/" | tr -d "\n"`
if [[ "$ccc" == "" ]]; then
jps
sleep 10s
ccc=`jmap -heap $pid | grep "used" | grep "(" | sed "s/.*(\(.*\)MB)/\1;/" | tr -d "\n"`
if [[ "$ccc" == "" ]]; then
jps
sleep 10s
ccc=`jmap -heap $pid | grep "used" | grep "(" | sed "s/.*(\(.*\)MB)/\1;/" | tr -d "\n"`
if [[ "$ccc" == "" ]]; then
jps
sleep 10s
ccc=`jmap -heap $pid | grep "used" | grep "(" | sed "s/.*(\(.*\)MB)/\1;/" | tr -d "\n"`
if [[ "$ccc" == "" ]]; then
fin=true
jps
fi
fi
fi
fi
fi
d=$(date)
echo "$d;$ccc" >> "$file_name_memory_csv"
#echo "$d"
#echo "Memory: $ccc"
u=`vmstat | tail -1 | sed  "s/\([0-9][0-9]*\) */\1;/13g" | sed "s/\([0-9][0-9]* \)//g"`
#echo "CPU : $u"
echo "$d;$u" >> "$file_name_memory_csv"
sleep 5m
done