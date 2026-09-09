/   ->starting point or the root directory

/home -> this is where user files are stored like files document downloads ect you may have multiple users for example /howe/klaos

/root -> is the root user or administrator of your account

/bin ->command line programs that linux needs to function   like ls cd mkdir pwd (think of it as the toolbox of linux to operate)

/sbin ->tools for managing the operating system  networking disk management booting the system 

/etc  ->where linux stores configuration files  every application has a configuration file inside /etc

/usr  -> all the application downloaded in the system libraries documentation tools also 

/tmp  -> where linux creates temperary files many application create temp files while they are running

/var -> keeps variables like logs 

/media  ->automaticly mounted devices like usb dvd 

/boot  ->this is where the linux starts  startup configuration bootloader files and the linux kernel

To sum it up

user files under /home
programs under /bin/sbin/usr
configuration files under /etc
shared libraries under /lib
logs and changing data under /var
temporary files under /tmp
hardware devices under /dev
system information under /proc
boot files under /boot
external storage under /media


This is how the linux kernel works:
terminal shows what you typed 
shell interprets it searches path for program
linux checks for user premissions
kernel creates a procces and gives the resources it needs 
program may read files pass output use the network run as a service or run in a container 
if something fails best way to check is /logs 