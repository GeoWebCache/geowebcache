package org.geowebcache.config;

public class PostgresResultSet<E> {

    private E obj;

    public PostgresResultSet() {
        }

    public PostgresResultSet(E obj) {
            this.obj = obj;
        }

    public E getObj() {
        return obj;
    }

    public void setObj(E obj) {
        this.obj = obj;
    }

}
