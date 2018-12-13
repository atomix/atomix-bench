# Dockerfile

FROM java:8

RUN apt-get update \
  && apt-get install -y python3-pip python3-dev \
  && cd /usr/local/bin \
  && ln -s /usr/bin/python3 python \
  && pip3 install --upgrade pip \
  && pip3 install requests \
  && pip3 install terminaltables

ARG VERSION
RUN mkdir -p /opt/atomix-bench
COPY target/atomix-bench.tar.gz /opt/atomix-bench/atomix-bench.tar.gz
RUN tar -xvf /opt/atomix-bench/atomix-bench.tar.gz -C /opt/atomix-bench && rm /opt/atomix-bench/atomix-bench.tar.gz

WORKDIR /opt/atomix-bench

EXPOSE 5678
EXPOSE 5679

ENTRYPOINT ["./bin/atomix-bench"]
