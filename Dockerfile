FROM openjdk:11

ARG db_driver_path
ARG db_driver_jar

RUN mkdir /app

ADD . /app/nri-db
ADD ./.nridbrc /root
ADD ${db_driver_path} /app/nri-db/lib

RUN chmod 0400 /root/.nridbrc

ENV CLASSPATH=/app/nri-db/lib/${db_driver_jar}

WORKDIR /app/nri-db

CMD ["./bin/nri-db"]
