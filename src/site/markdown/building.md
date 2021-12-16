# Building pg-embedded

## Code compilation and testing

`pg-embedded` uses [Apache Maven](https://maven.apache.org/) as its build system.

The code uses the maven-wrapper for local builds.


```bash
% ./mvnw clean install
```

At least Java 11 is required for building. While the code should build with any Java version post 11, only LTS versions (currently 11 and 17) are supported.

The resulting library runs on Java 11 and beyond.

## Documentation site

The site is generated using the maven site plugin:

```bash
% ./mvnw clean install site
```

Note that omitting the `install` step may result in Javadoc links not working correctly (see [MJAVADOC-701](https://issues.apache.org/jira/browse/MJAVADOC-701) for an explanation of the problems).
