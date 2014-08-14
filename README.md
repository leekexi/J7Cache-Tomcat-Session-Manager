J7Cache-Tomcat-Session-Manager
==============================
## Abstract
J7Cache Tomcat Session Manager is a Tomcat cluster Session shared library, you can put Novo dry Tomcat bundled together to form a cost-efficient cluster.


## Required third-party packages

```
catalina.jar
commons-beanutils.jar
commons-collections.jar
commons-lang.jar
commons-logging.jar
ezmorph-1.0.6.jar
json-lib-2.4-jdk15.jar
servlet-api.jar
slf4j-api-1.7.5.jar
slf4j-log4j12-1.7.2.jar
tomcat-juli.jar
```

## Add the following into your tomcat context.xml
```
<Valve className="com.j7.session.J7SessionHandlerValve" />
<Manager className="com.j7.session.J7SessionManager" host="$J7CacheServer IP"  port="$J7CacheServer Listening port"  database="J7SESSIONS"  maxInactiveInterval="600" />
```

## Get J7Cache Server
https://github.com/leekexi/J7Cache


## Reference materials, thanks
https://github.com/jcoleman/tomcat-redis-session-manager
