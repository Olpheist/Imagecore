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

Navigate to the hosted link below:  
https://imagecore.org/

To run the application and database locally, you can simply run the following command from the root of the repository

```shell
bash scripts/run_application.sh
```
The `run_application.sh` script is designed to orchestrate the startup of both the Spring Boot backend and the Nuxt frontend. Because it is a Bash script, it requires a Unix-like environment to function correctly.

This script checks to ensure you have the necessary dependencies, builds the frontend, and starts the postgres and application server.

You can view the application locally at [http://localhost:8080](http://localhost:8080)

## Application Navigation Flow

1. From the landing page, click **Login**.
2. On the login page, select **Register**.
3. After successfully registering, you are redirected back to the **Landing Page**.
4. Open the **top-right navigation menu** and select **Dashboard** to access your dashboard.
5. If logged in as admin, select the **Admin Center** to view and manage user accounts and inspect recent log application
6. On the **Dashboard**, if you have the CLINICIAN role, you can access the the Upload DICOM Image widget
7. On the **Dashboard**, if you have the CLINICIAN, RESEARCHER, or ADMIN role, you can access the Tools page
8. On the **Dashboard**, if you have the CLINICIAN, RESEARCHER, or ADMIN role, you can access the DICOM Image Viewer

**Prerequisites by Operating System**

| System  | Tool Required  | Execution Command  |
|---|---|---|
| Windows  | WSL2 (Ubuntu) or Git Bash  | `bash scripts/run_application.sh`  |
| Linux / macOS  | Native Terminal  | `bash scripts/run_application.sh`  |

> [!NOTE]
> If you have another application running on port 8080, stop that process before running this application.


If you created or edited the script on Windows, it likely contains hidden Carriage Return characters `'\r'` that will cause the script to fail in Bash.

**Symptoms of the error if encountered:**  
`scripts/run_application.sh: line 2: $'\r': command not found`  
`set: pipefail: invalid option name`

**To fix:**  
You must convert the file from CRLF to LF (two options)   
Via Command Line (WSL/Git Bash):
```
sudo apt install dos2unix  # If not installed
dos2unix scripts/run_application.sh
```
**Via IDE:**  
Open the script  
Click CRLF in the bottom-right status bar  
Select LF  
Save the file


## Licensing

For more information regarding use and commercialisation of this software, you can view the license at [`LICENSE`](LICENSE)
