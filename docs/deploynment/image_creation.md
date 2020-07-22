## Создание образа

Как сделать локальный репозиторий:

- https://wiki.debian.org/DebianRepository/Setup#trivialArchive
  - Основная инструкция, учитывающая как нужно правильно запускать
    `dpkg-scanpackages` (из корня репозиторий, пути файлов запоминаются
    относительно от **CWD**).
- https://askubuntu.com/questions/170348/how-to-create-a-local-apt-repository
- **Обязательно проверить доступность всех родительских директорий на `r-x`**
- Формат репозитория Debian
  - https://wiki.debian.org/DebianRepository/Format

Как упаковать в образ:

```sh
# В Debian mkisofs называется genisoimage, в пакете genisoimage
mkisofs -iso-level 4 -full-iso9660-filenames -relaxed-filenames -o backup.iso backup/
```

- https://www.debian.org/doc/manuals/repository-howto/repository-howto.en.html
- https://wiki.debian.org/ru/DebianCustomCD
- https://blog.heckel.io/2015/10/18/how-to-create-debian-package-and-debian-repository/
- https://wiki.debian.org/DebianInstaller/Modify/CD
- https://www.opennet.ru/base/sys/dvd_from_deb_rep.txt.html

---

## Использование образа

1. Переместить файл образа в систему

Сделать любым из доступных способов. Файл образа должен находиться в файловой
системе и быть доступным для монтирования.

2. Примонтировать образ в /media/cdrom

```sh
sudo mount -o check=r,map=o <путь_до_образа.iso> /media/cdrom/
```

3. Добавить локальный репозиторий

```sh
# Когда программа спросить, ввести имя носителя. Например, AstraIonPackages,
# это не принципиально.
sudo apt-cdrom -m add

# Обновить базу пакетов
sudo apt-get update
```

