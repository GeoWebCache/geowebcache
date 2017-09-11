package org.geowebcache.controller;

import org.geowebcache.GeoWebCacheDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * Top-level dispatcher controller
 */
@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}")
public class GeoWebCacheDispatcherController {

    @Value("${gwc.context.suffix:}") String prefix;

    @Autowired
    @Qualifier("geowebcacheDispatcher")
    private GeoWebCacheDispatcher gwcDispatcher;

    @RequestMapping(path = {"", "/home","/service/**", "/demo/**", "/proxy/**"})
    public void handleRestApiRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        gwcDispatcher.handleRequest(new HttpServletRequestWrapper(request) {
            @Override
            public String getContextPath() {
                return super.getContextPath() + ("".equals(prefix) ? "" : "/" + prefix);
            }
        }, response);
    }
}
