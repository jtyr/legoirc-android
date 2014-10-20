LEGO IR Controller
==================

Description
-----------

This is an Android application which is trying to provide an alternative to the
original LEGO IR controller. It allows to control the LEGO Technic vehicle (e.g.
[9398 4x4
Crawler](http://www.lego.com/en-gb/technic/products/speed/9398-4x4-crawler)) by a
through an IR transmitter controlled by the [Raspberry
Pi](http://www.raspberrypi.org) installed on the vehicle.

The application communicates with the Raspberry Pi through Wifi signal which
allows to extend the operation range and makes the control of the vehicle less
prone to signal loss which is often experienced with the original IR controller.
This solution also allows to use the [Raspberry Pi
Camera](http://www.raspberrypi.org/products/camera-module/) to stream the
realtime video from the vehicle and display it on the background of the
controller.

The hardware and software requirements are described on the project site of the
[LEGO IR Controller Server](https://github.com/jtyr/legoirc-server).

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=cz.tyr.android.legoirc2)


License
-------

This software is licensed by the MIT License which can be found in the file
[LICENSE](http://github.com/jtyr/legoirc-server/blob/master/LICENSE).
