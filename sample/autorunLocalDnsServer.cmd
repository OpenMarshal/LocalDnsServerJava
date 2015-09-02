@echo off

ipconfig /flushdns

F:
cd F:\LocalDnsServer
wscript.exe "autorunDescriptor.vbs" "LocalDnsServer.jar"

cls