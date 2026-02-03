FROM alpine:3.19
RUN apk add --no-cache socat
EXPOSE 1338
CMD ["socat", "TCP-LISTEN:1338,fork,reuseaddr", "TCP:169.254.169.254:80"]
