Setup / Data Scripts

1) Start dependencies (MySQL, Redis, RocketMQ)

docker-compose up -d

2) Initialize database tables

Use init.sql to create the tables:
- user_points
- points

If you already have an existing database, apply the SQL in init.sql manually.

3) Application config

Make sure application.yaml has the correct connection settings:
- spring.datasource.url
- spring.datasource.username / password
- spring.data.redis.host / port
- rocketmq.name-server

4) Run the application

./mvn spring-boot:run
