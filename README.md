<<<<<<< HEAD
# SkillPulse Spring Boot Backend

This keeps your existing HTML/CSS/JS UI and adds a Java Spring Boot backend around it.

## Best architecture for your project

- Use **Spring Boot** for login/register, APIs, dashboard data, and serving HTML.
- Keep **Python** for ML experiments and models, because the ML ecosystem is much stronger there.
- Let Spring Boot expose `/api/ml/analyze`; internally it can use the Java fallback or call Python.

## Run

This project targets **Java 8** with **Spring Boot 2.7.18**.

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080/
```

## API endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me?token=...`
- `GET /api/dashboard/summary?token=...`
- `GET /api/skills`
- `POST /api/ml/analyze`
- `GET /api/ml/health`

## ML mode

Default mode is Java fallback:

```properties
skillpulse.ml.mode=java
```

To use the Python wrapper:

```properties
skillpulse.ml.mode=python
skillpulse.ml.python.command=python
```

Install Python packages only when you replace the lightweight wrapper with your full ML model.
=======
# SkillPulse
>>>>>>> 579d6b1034407b4e2d3a2050e336d43153ed2eaf
