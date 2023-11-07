# SerialPipe
UDP to USB Serial bridge on Android  
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/io.github.wh201906.serialpipe)

This app works as a bridge to transfer the communication between the UDP and USB serial port. It has the same function as the following command in Linux:
```
socat -dd /dev/ttyACM0,raw,echo=0,b<baudrate> udp4-listen:<port>,reuseaddr,fork
```
