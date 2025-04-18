Tidy up aws after working with tests
===

```
aws s3 ls s3://<bucket>/ | grep tmp_ | awk '{print $2}' | while read obj; do
    echo "Object: $obj"
    aws s3 rm s3://gwc-s3-test/$obj --recursive
done
</code>
```

Replace the `<bucket>` with the value configured in your system.
This will delete all the temporary object that have been created


Config file
====
Add a `.gwc_s3_tests.properties` to your home directory to get the integration tests to run. 

```
cat .gwc_s3_tests.properties
```
_contents of file_

```
bucket=gwc-s3-test
secretKey=lxL*****************************
accessKey=AK***************```

```
