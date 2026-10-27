/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.geowebcache.rest.controller;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;
import org.geowebcache.rest.exception.RestException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RunWith(Enclosed.class)
public class RestExceptionHandlerTest {

    @RestController
    static class FailingController {
        @GetMapping("/not-found")
        public void notFound() {
            throw new RestException("Unknown layer: missing", HttpStatus.NOT_FOUND);
        }

        @GetMapping("/bad-request")
        public void badRequest() {
            throw new RestException("Layer name not provided", HttpStatus.BAD_REQUEST);
        }

        @GetMapping("/server-error")
        public void serverError() {
            throw new RestException("Truncation failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @GetMapping("/broken")
        public void broken() {
            throw new IllegalStateException("Broken layer");
        }
    }

    /** Stands in for the catch-all advice of a host application such as GeoServer. */
    @ControllerAdvice
    static class CatchAllAdvice {
        @ExceptionHandler(Exception.class)
        public ResponseEntity<String> handleAnything(Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    static MockMvc mockMvc(Object... controllerAdvice) {
        return MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(controllerAdvice)
                .build();
    }

    public static class AsOnlyAdvice {

        private MockMvc mockMvc;

        @Before
        public void setUp() {
            mockMvc = mockMvc(new RestExceptionHandler());
        }

        @Test
        public void testNotFound() throws Exception {
            mockMvc.perform(get("/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Unknown layer: missing"));
        }

        @Test
        public void testBadRequest() throws Exception {
            mockMvc.perform(get("/bad-request"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Layer name not provided"));
        }

        @Test
        public void testInternalServerError() throws Exception {
            mockMvc.perform(get("/server-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Truncation failed"));
        }

        @Test
        public void testOtherExceptionsNotHandled() {
            ServletException unhandled = assertThrows(ServletException.class, () -> mockMvc.perform(get("/broken")));

            assertTrue(unhandled.getCause() instanceof IllegalStateException);
        }
    }

    /**
     * Spring loads advice beans of equal precedence in registration order, which is a race following on the order of
     * the jars in {@code WEB-INF/lib}. A catch-all advice registered first must not take over a {@link RestException},
     * and must still get every other exception.
     */
    public static class RegisteredAfterCatchAllAdvice {

        private MockMvc mockMvc;

        @Before
        public void setUp() {
            mockMvc = mockMvc(new CatchAllAdvice(), new RestExceptionHandler());
        }

        @Test
        public void testRestExceptionStatus() throws Exception {
            mockMvc.perform(get("/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("Unknown layer: missing"));
        }

        @Test
        public void testOtherExceptionsLeftToCatchAllAdvice() throws Exception {
            mockMvc.perform(get("/broken"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string("Broken layer"));
        }
    }
}
