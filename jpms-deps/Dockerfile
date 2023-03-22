ARG BASE_IMAGE_REGISTRY_URL=""
FROM alpine:3.17

###################################################
# Run updates and add required packages
###################################################
RUN apk update && \
    apk add --no-cache openjdk19~=19.0 maven~=3.8