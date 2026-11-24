# ec-lakehouse

End to end data pipeline for a multi-vendor e-commerce application.

## Data

- Download the dataset from Kaggle: (Brazilian E-Commerce Public Dataset by Olist)[https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce/data]
- To ensure ease, make sure to go to the root directory, and run the commands
```bash

#!/bin/bash
kaggle datasets download olistbr/brazilian-ecommerce
unzip brazilian-ecommerce.zip -d data

```
> Note: You need to install kaggle-cli through `pip`. I used `pipx` because of pip requiring a venv.
## API

The multi-vendor e-commerce API that produces the data lives in [`api/`](api/README.md): Spring Boot microservices (gateway, auth, vendor, catalog, cart, order, payment), each with its own Postgres database and Docker image.
