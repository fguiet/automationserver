Based on docker image https://hub.docker.com/r/openhab/openhab/

Starting openhab docker
------------------------

1. Run AutomationServer/third-party-tools/openhab/install/prerequisite

It will copy custom conf to shared folder

2. Launch openhab docker : AutomationServer/third-party-tools/openhab/docker/openhab_dockerrun

openhab can be open via : http://openhabip:8080/basicui/app

3. In OpenHab2

Binding
Add Astro Binding
Add NTP Extension
Add Http Binding

Things
Add Astro moon data
Add Astro sun data
Add Local Time

In item configure moon, sun item with correct id astro moon/sun thing

Create /openhab/userdata/webapps folder

cd ~/applications/openhab/userdata
sudo mkdir webapps

Copy /openhab/conf/html/weather-data in /openhab/userdata/webapps

sudo cp -r ~/applications/openhab/conf/html/weather-data/ webapps/

Change owner
sudo chown -R openhab:openhab webapps/

Restarting Openhab due to memory leak
-------------------------------------

mkdir -p /home/fred/applications/crontab
cp ~/git/AutomationServer/third-party-tools/openhab/install/restart_openhab /home/fred/applications/crontab/

crontab -e
0 0 * * 1,4 /home/fred/applications/crontab/restart_openhab


