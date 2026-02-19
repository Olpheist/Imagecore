[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/SLVYXqRB)

# Project Setup

IntelliJ + WSL users:
- Ensure a Java SDK named exactly 17 exists in Project Structure → SDKs
- Gradle toolchains require this exact name

## Frontend
Directory: `client`

Requires: Node.js v24
```bash
npm install
npm run dev
```

## Backend
Directory: `server`


Requires: Java 17, Docker

1. Link or open the Gradle project
2. Start the development database by running `docker-compose-dev.yml` as a container
3. Run the Spring Boot application from the Gradle project

## Running the Project
To run the application and database locally, you can simply run the following command from the root of the repository

```shell
bash scripts/run_application.sh
```

This script checks to ensure you have the necessary dependencies, builds the frontend, and starts the postgres and application server.

You can view the application locally at [http://localhost:8080](http://localhost:8080)

> [!NOTE]
> If you have another application running on port 8080, stop that process before running this application.


## Licensing

For more information regarding use and commercialabilty of this software, you can view the license at [`LICENSE`](LICENSE)