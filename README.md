LEGO IR Controller
==================

Description
-----------

This is an Android application which is trying to provide an alternative to the
original LEGO IR controller. It allows to control the LEGO Technic vehicle (e.g.
[9398 4x4
Crawler](http://www.lego.com/en-gb/technic/products/speed/9398-4x4-crawler)) by a
smartphone application which communicates with the car's IR receiver through an
IR transmitter controlled by the [Raspberry Pi](http://www.raspberrypi.org)
installed on the vehicle. The smartphone communicates with the Raspberry Pi
through Wifi signal which allows to extend the operation range and makes the
control of the vehicle less prone to signal loss which is often experienced with
the original IR controller. This solution also allows to use the [Raspberry Pi
Camera](http://www.raspberrypi.org/products/camera-module/) to stream the
realtime video from the vehicle and display it on the screen of the smartphone.
The installation is non-invasive and doesn't require any modification of the
vehicle.

The hardware and software requirements and its installation is described on the
project site of the [LEGO IR Controller
Server](https://github.com/jtyr/legoirc-server).


License
-------

This software is licensed by the MIT License which can be found in the file
[LICENSE](http://github.com/jtyr/legoirc-server/blob/master/LICENSE).
