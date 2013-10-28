red5-mobileconsole
==================

Red5 for JBoss 7, with JMS-based eventing and iOS/Android streaming

What is it?
---------------------

red5-ear.ear:
- Red5 bundled as an ear that deploys on JBoss 7, and now Wildfly 8.0.0.Alpha4 (currently using 7.1.3.Final on drdenver.tv)

red5.war:
 - Red5, more or less untouched (need to compare any changes I made against current SVN, years ago), with a jboss deployment structure to get everything loaded

live.war:
 - Red5 "Application" that sends events over JMS to Topics that can consume things like viewer subscribes/unsubscribes to a stream in the context
 - live.war also uses JMS to take commands from mobileConsole.war, to do things like start or stop an Pantos/HLS transcode (Humble-Video/FFMPEG), or start or stop a recording (lots of hardwired paths, watch out :) )

mobileConsole.war:
 - Subscribes to the Topics for live.war to get information on what's transcoding, and who's watching
 - Sends control messages to live.war for things like starting and stopping transcodes and records

Author note:
---------------------
* This code was developed for my personal project, https://www.drdenver.tv
* Over the next few weeks, I intend to clean things up and generalize the code as I have time
* The Xuggler code in live.war works only after hard-coding the segment settings (how many, what file name pattern, etc.) in FFMPEG's source, and compiling
* Currently moving to support Art Clarke's Humble-Video. Looks similar to Xuggler after an initial build (comitted 9/25/13)
* #1 on my todo is creating a walkthrough on how to get this going for you in Eclipse, and how to deploy to a generic Linux host, so you can test the project and start streaming

FAQ:
---------------------
* Q: It's broken and there's no documentation, why is this here?
* A: Because everyone should release their FOSS-derived works. I hope to make it generally usefull as I have time.
* Q: Does it work?
* A: Yes: https://www.drdenver.tv on your Android or iOS device.

Contact:
---------------------
* Use my github contact information here: https://github.com/cwpenhale
* Feel free to ask any questions you want, chances are I'll be stoked to help you.
