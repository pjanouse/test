Compares EAP 6.4.0 server structure to running server.

1. run EAP server
2. application can be run from IDE, main class Dumpler.class
3. in class Dumper boolean ADD_WHOLE_DIFFERENT_SUBTREES defines whether add whole subtree of different node to result or not.
4. output in project folder files
	-Result_all.txt		- contains all changes
	-Result_renamed.txt	- contains only renamed fields 
	-Result_differences.txt - contains only changes in structure without renamed fields


Output example

EAP6 + allow-failback		 [ subsystem=messaging/children/hornetq-server/model-description/*/attributes ] 
Server name + name of field which second server doesnt have [ path in server structure ]
