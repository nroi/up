# up

A simple web app that allows you to upload arbitrary files. Written in Scala with the Play Framework.

Users uploading a file must enter a token, which is a one-time key used for authorization.
New tokens can be generated by sending a signed POST request. POST requests are signed and
verified by using a shared private key with HMACs.

## Configuration and installation

Create a key file and put it on the server and each client. The key file must be a valid UTF-8 file. The server expects the
key file on `$HOME/key`, for the client, change the [up_token_gen](https://github.com/nroi/up/blob/master/client/up_token_gen) script
accordingly.

The application is meant to be used in conjunction with a reverse proxy such as NGINX. Files uploaded using `up` are stored in a
directory (`$HOME/f` by default), so the reverse proxy should be configured to relay all requests to the backend, except for
GET requests used to fetch a previously uploaded file, for example:
```NGINX
server {
    listen [::]:80;
    listen 80;
    server_name up.helios.click;
    location / {
        proxy_set_header X-Real-IP  $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Host $http_host;
        proxy_redirect off;
        proxy_pass http://127.0.0.1:9000;
    }
    location /f/ {
        rewrite ^/f/(.*)$ /$1 break;
        root /home/up/f;
    }
}
```

The server application is meant to be installed and used on Debian with systemd, using the
[sbt-native-packager](http://sbt-native-packager.readthedocs.io/en/v1.1.6/#).
Run
```
sbt debian:packageBin
```
and install the resulting .deb file. Notice that Debian will start `up` automatically after installation, but the startup
process will fail since the application is not fully configured just yet.

The Play! Framework needs an application secret for signing CSRF tokens. Change the value in `/etc/up/application.conf`
accordingly, see the [Play! documentation](https://www.playframework.com/documentation/2.5.x/ApplicationSecret) for more details.
Also, change the `play.filters.hosts` and `play.hostname` variable in the same file.
Create the directories `/home/up/f`, `/home/up/private` and `/home/up/hash`, the user `up` needs
write access to all those directories.

Restart the application using systemd:
```
systemctl restart up
```

## Usage

Generate a new token using the [up_token_gen](https://github.com/nroi/up/blob/master/client/up_token_gen) script. Visit the
web interface, choose the file, enter the token, and click "Upload". Alternatively, you can use the
[up_public](https://github.com/nroi/up/blob/master/client/up_token_gen) to upload a file directly, without having to visit
the web interface in your browser.
