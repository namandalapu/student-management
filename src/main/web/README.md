# Student Management web UI

The Angular 18 web UI for Student Management. It is part of the single Spring Boot project
(see the README in the project root), not a separate app.

- `./mvnw spring-boot:run` from the project root builds this UI into `target/classes/static`
  and serves it with the REST API at http://localhost:8080.
- For live reload while editing the UI, run the server with
  `./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm`, then `npm install` and `npm start`
  in this folder and open http://localhost:4200 (`/api` is proxied to port 8080, see `proxy.conf.json`).
- Unit tests: `npm test -- --watch=false --browsers=ChromeHeadless`
