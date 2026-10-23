# Commit-Crew-HI

A bakery-focused food ordering and delivery simulator developed for **SOEN 343 – Software Architecture and Design**.

The system allows customers to order bakery products, bakery staff to manage and prepare orders, couriers to deliver them, and operators to monitor the platform.

## Team Members

| Name | Student ID | Role |
|---|---:|---|
| Wiâme Kotbi | 40299105 | Repository Coordinator & Software Developer |
| Maria Laghmari | 40297167 | Team Lead & Software Developer |
| Aya Manguer | 40298292 | Requirements & Documentation Coordinator & Software Developer |
| Anthony Ly | 40282737 | Design Coordinator & Software Developer |
| Quentin Malichecq-Laroche | 40285143 | Quality Coordinator & Software Developer |
| Sukhdarshan Singh | 40240213 | Integration Coordinator & Software Developer |

**Team Leader:** Maria Laghmari

## Main Users

- Customer
- Bakery staff
- Courier
- Platform operator

## Main Features

- User login and role-based access
- Browse bakery menus
- Place and manage orders
- Manage bakery items and availability
- Prepare orders in arrival order
- Assign ready orders to couriers
- Confirm pickup and delivery
- Calculate order prices
- View order history
- Display restaurants, couriers, and orders on the simulator dashboard

## Technology Stack

- **Backend:** Java + Spring Boot
- **Frontend:** HTML, CSS, JavaScript
- **Version Control:** Git + GitHub
- **Modelling:** Draw.io 
- **Testing:** JUnit.

## Project Modules

- **REG** – Registration, Login & Profiles
- **ORD** – Order & Bakery Core
- **DLV** – Courier & Delivery
- **DASH** – Simulator Dashboard
- **PRC** – Pricing, Billing & Order History

## Platform Requirements

- Java 17 or newer
- Windows, macOS, or Linux
- PostgreSQL/Supabase database
- Internet access for Maven dependencies

Maven does not need to be installed because the Maven Wrapper is included.

## Database Configuration

Set the Supabase environment variables before running the application.

### Windows PowerShell

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME="YOUR_USERNAME"
$env:SUPABASE_DB_PASSWORD="YOUR_PASSWORD"
```

### macOS/Linux

```bash
export SUPABASE_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require"
export SUPABASE_DB_USERNAME="YOUR_USERNAME"
export SUPABASE_DB_PASSWORD="YOUR_PASSWORD"
```

## Compile and Test

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd clean test
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw clean test
```

## Run Locally

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd spring-boot:run
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw spring-boot:run
```

Then open:

```text
http://localhost:8080/login.html
```

The application runs on port `8080` by default.

## Build

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd clean package
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw clean package
```

The generated JAR can then be run from the `target` folder.

## Demo Accounts

- Customer: `customer1` / `customer123`
- Restaurant staff: `staff1` / `staff123`
- Courier: `courier1` / `courier123`
- Operator: `operator1` / `operator123`

## Repository

This repository contains the source code, diagrams, tests, and documentation for the project.

More detailed requirements, architecture decisions, use cases, and design patterns are documented in the project report.
