Сюда кладутся jar-ники зависимостей (в наш jar они НЕ вшиваются):
  doomedmatu-0.4.7.3.jar   (обязательно)
  doomedguns-0.2.x.jar     (обязательная зависимость самого Doomed)
build.gradle подключает ВСЕ *.jar из этой папки автоматически (flatDir + fg.deobf).
