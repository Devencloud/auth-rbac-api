Login and Role-Based Access Control API

A stateless REST API built with Spring Boot. Users can register and log in, and access to endpoints depends on their role (ADMIN or USER). Authentication uses JWT access tokens, with a database-backed refresh token for logout.

Tech stack
Java 21, Spring Boot 4.1.2, Maven
Spring Security, Spring Data JPA, Bean Validation
MySQL 8
jjwt 0.12.6 for JWT
BCrypt for password hashing
How to run

1. Create the database (MySQL must be running):

sql
CREATE DATABASE auth_rbac_db;

2. Create a file named .env in the project root (next to pom.xml):

DB_PASSWORD=your_mysql_root_password
JWT_SECRET=any-random-string-of-at-least-32-characters
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=choose-a-password
The database user is root on localhost:3306. To change it, edit spring.datasource.username or spring.datasource.url in src/main/resources/application.properties.
ADMIN_EMAIL and ADMIN_PASSWORD define your own admin account (see "How the admin is created" below).
The app must be started from the project root, so that .env is found.

3. Start the app:

mvn spring-boot:run

The API runs on http://localhost:8080. Tables are created automatically on first start.

Endpoints
Method	Path	Access	Description
POST	/api/auth/register	Public	Create a new user (role is always USER)
POST	/api/auth/login	Public	Returns an access token and a refresh token
POST	/api/auth/refresh	Public (needs refresh token)	Returns a new access token
POST	/api/auth/logout	Public (needs refresh token)	Deletes the refresh token
GET	/api/users/me	Any logged-in user	The caller's own profile
GET	/api/users	ADMIN only	List all users
GET	/api/users/{id}	ADMIN only	Get one user by id

Protected endpoints need the header Authorization: Bearer <accessToken>.

Example requests

Register:

bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user1@example.com","password":"user0001"}'

Login (the response contains accessToken and refreshToken):

bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user1@example.com","password":"user0001"}'

Own profile:

bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"

List all users (log in as the admin from your .env to get an admin token):

bash
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer <adminAccessToken>"

Get an access token using the refresh token:

bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

Logout:

bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

On Windows, the quotes in -d may need escaping in Command Prompt. Postman or Git Bash avoids that.

Responses and status codes
Status	When
201	User registered
200	Login, refresh, and read endpoints succeeded
204	Logout succeeded
400	Invalid input (bad email, password not 8 to 72 characters, missing field)
401	Missing, invalid or expired access token. Also wrong login credentials and invalid refresh tokens
403	Valid token, but the role is not allowed (a USER calling an admin endpoint)
404	User id not found
409	Email already registered
How the admin is created

Registration can never create an admin: the request has no role field, and the code always assigns USER.

Instead, on startup the app creates one admin from ADMIN_EMAIL and ADMIN_PASSWORD in .env. If a user with that email already exists, nothing is changed. If the variables are empty, no admin is created. This way, every person who runs the project sets their own admin, and no credentials are stored in the code.

Design decisions
Stateless authentication. No sessions. Every request carries a JWT, which a filter validates. CSRF protection is disabled because the API uses no cookies, and a token in the Authorization header is not sent automatically by browsers.
Short-lived access token (10 minutes). A JWT cannot be revoked, so it is kept short. It contains the user's email and role.
Refresh token stored in the database. It is a random UUID valid for 7 days. Logout deletes it, so it can no longer be used to get new access tokens. An already-issued access token stays valid until it expires, at most 10 minutes.
401 vs 403. 401 means "we don't know who you are" (no or bad token). 403 means "we know who you are, but you are not allowed".
Same error for wrong email and wrong password. Login returns the same 401 message in both cases, so the API does not reveal which emails are registered.
Access rules in one place. All role rules are in SecurityConfig, and anything not listed requires authentication.
DTOs only. Entities are never returned, so the password hash never appears in a response.
Secrets from the environment. The database password, JWT secret and admin credentials come from .env, which is not committed.
/api/users/me reads the caller's identity from the token, never from the URL, so a user can only see their own profile.
Known limitations
Expired refresh tokens are deleted only when someone tries to use them. A scheduled cleanup job would be the production fix.
The refresh token is not rotated on use, and it is stored in plain text. Production systems usually rotate it and store only a hash.
spring.jpa.hibernate.ddl-auto=update creates the tables automatically. A migration tool such as Flyway would be used in production.
No automated tests are included.
Project structure
src/main/java/com/nexbrivo/authrbac/
├── config/        AdminSeeder (creates the admin on startup)
├── controller/    AuthController, UserController
├── dto/           Request and response objects
├── entity/        User, RefreshToken
├── repository/    UserRepository, RefreshTokenRepository
├── security/      SecurityConfig, JwtService, JwtAuthenticationFilter
└── service/       AuthService, UserService
