# Features

## Definition (from wikipedia.org) 

> Castor JDO is an open source data binding framework for moving data from Java programming language objects to databases.
           
           
Castor JDO is made up of (independent) modules as follows:
        

* Castor JDO - Persistence framework
* Castor JDO - DDL generator
* Additional tools
* Integration with other frameworks

## Castor JDO

Java persistence framework to bind Java objects to database tables.
            
* XML-based JDO mapping file to specify bindings for (existing) object models.
* Support for ODMG OQL queries.
* EJB container managed persistence provider for OpenEJB
* Supports two-phase commits, object rollback and deadlock detection.
* Support for in-memory caching (various cache providers).
* Support for *write-at-commit* to reduce JDBC operations.

Castor JDO is not the same-as or compatible with Sun's JDO. We have a different approach to handling data object to RDBMS mappings.
            
        
## Castor JDO DDL generator
        
Generates DDL statements from JDO mapping files.


## Integration with other frameworks
        
Castor JDO is currently integrated with the following frameworks or has support for being integrated:
        
* [Spring ORM](./spring-orm-integration.html) support for Castor

