# Whanos

## kubernetes

- use a template filed with the value of the whanos.yaml
- deployment name is the project name in the namespace named user

## Jenkins Local Setup

Setup a password for the admin account

```
echo "ADMIN_PASSWORD=o" | tee infra/jenkins/.env
```

run:

```bash
./script/setup fresh
```
