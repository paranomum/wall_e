#!/bin/sh
/bin/sh mvnw
rm -rf out
java -jar /Users/admin/Desktop/wall_e/modules/openapi-generator-cli/target/openapi-generator-cli.jar generate \
  -i https://iqhr-questionnaire-service-https-iqhr.apps.okd.stage.digital.rt.ru/api/questionary/v3/api-docs/questionery \
  --library webclient \
-o /Users/admin/Desktop/wall_e/out -g java