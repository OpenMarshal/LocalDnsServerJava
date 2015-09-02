# LocalDnsServer
Local DNS Server to bring more control over the OS

# Description

This program allow the user to redirect the requests through this software to :
* Monitor the DNS requests (spot spywares, weird domains, etc)
* Filter the DNS requests

Indeed, if a software can't obtain the IP of it's server, so it will not be able to send any request. But most of viruses use direct IP, no DNS requests, so this software will not protect from most of viruses but it will bring a better control over not trusted softwares like Google Chrome which can send private information to a server (this way you can spot this activity and block it).

While the program is running, it will use the "filter.txt" file to know which domain to filter and which one to allow. It shows too (in diagnosis mode [default mode]) the domains requested by your computer, allowing you to see clearly the domains reached.

In the folder "sample" in this repo, you can find :
* The .jar used to run the program.
* An example of the .txt used to filter the DNS requests.
* The .vbs file used by Windows to autorun the program at startup.
* An example of the .cmd used by Windows to autorun the program at startup.

# Installation

The installation consists to change the DNS server which will be used by your operating system to send DNS requests.
This software is a DNS server hosted in local ; so you will have to change the IP of the DNS server to use into "127.0.0.1".

### Windows [XP | Vista | 7 | 8.1 | 10]

To do so, in Windows, you can follow this tutorial : http://mintywhite.com/windows-7/change-dns-server-windows-7/ .
In the "Use the following DNS server addresses" at step 5, your will put on the first line : "127.0.0.1" and on the second line nothing or an IP that you may be using if you encounter a problem with the software (in France I use "212.27.40.241").

# Run the program

To run the program manually, you have to start a command prompt, then use the following command :
```cmd
java -jar my/path/LocalDnsServer.jar
```

# Autostart

The autostart part has to be preceded by the Installation part.

### Windows [XP | Vista | 7 | 8.1 | 10]

You have to put together the files :
* LocalDnsServer.jar
* filter.txt
* autorunDescriptor.vbs

Then, open a text editor with a file called "xxxxx.cmd" (with xxxxx, the name of your choice) and write inside :

```cmd
@echo off   :: remove command input to display

:: We clear the DNS Cache
ipconfig /flushdns

:: We redirect the current location of the current program into 'pathOfTheFiles'
cd pathOfTheFiles
:: Execute 'LocalDnsServer.jar' located in 'pathOfTheFiles' in "ghost" mode, thanks to 'autorunDescriptor.vbs'.
wscript.exe "autorunDescriptor.vbs" "LocalDnsServer.jar"

cls    :: clear the screen
```

##### Note no.1
If your "pathOfTheFiles" is not on your system drive, you have to add it on the command file.
Here is an example for the drive "D" :

```cmd
@echo off

ipconfig /flushdns

:: The drive where pathOfTheFiles is. Don't forget the ':' after the letter.
D:
cd pathOfTheFiles
wscript.exe "autorunDescriptor.vbs" "LocalDnsServer.jar"

cls
```

##### Note no.2
If you want the program to pop up instead of being hidden, you will just call the program this way :

```cmd
@echo off

ipconfig /flushdns

D:
cd pathOfTheFiles

:: Run 'java' with the parameter '-jar' saying that the program will have to execute a .jar.
java -jar LocalDnsServer.jar

cls
```

So this way you don't need the file "autorunDescriptor.vbs" anymore.

Copy this command file into "C:\ProgramData\Microsoft\Windows\Start Menu\Programs\Startup". It will automatically run the program on startup of the user session.
