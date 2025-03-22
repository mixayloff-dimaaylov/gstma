# Run AppFromDB to recompute on time period from ClickHouse and save to data/
[no-cd]
[positional-arguments]
@fromdb *args='':
  mkdir -p data/
  docker compose run -v "${PWD}/data:${PWD}" -w $PWD spark-fromdb clickhouse:8123 "${@}"
