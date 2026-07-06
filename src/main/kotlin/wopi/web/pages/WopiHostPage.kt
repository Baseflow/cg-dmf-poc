// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.web

import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.ScriptType
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import java.net.URI

fun HTML.wopiHostPage(wopiClientUrl: URI, wopiSrcUrl: URI, accessToken: String, accessTokenTtl: Long) {
    head {
        meta { charset = "UTF-8" }
        meta {
            httpEquiv = "x-ua-compatible"
            content = "ie=edge"
        }
        meta {
            name = "viewport"
            content = "width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no"
        }

        title("WOPI Host Page")

        style("text/css") {
            unsafe {
                raw(
                    """

            body {
                margin: 0;
                padding: 0;
                overflow: hidden;
                -ms-content-zooming: none;
            }
            
            #office_frame {
                width: 100%;
                height: 100%;
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                margin: 0;
                border: none;
                display: block;
            }

                """.trimIndent(),
                )
            }
        }
    }
    body {
        form {
            id = "office_form"
            name = "office_form"
            target = "office_frame"
            method = FormMethod.post

            input {
                name = "access_token"
                value = accessToken
                type = InputType.hidden
            }
            input {
                name = "access_token_ttl"
                value = accessTokenTtl.toString()
                type = InputType.hidden
            }
        }

        span {
            id = "frame_holder"
        }

        script(type = ScriptType.textJavaScript) {
            unsafe {
                raw(
                    """

            function createActionUrl() {
                const actionUrl = URL("$wopiClientUrl")

                actionUrl.searchParams.set('wopiSrc', '$wopiSrcUrl');
                return actionUrl;

            }

            const frameHolder = document.getElementById('frame_holder');
            const officeFrame = document.createElement('iframe');
            officeFrame.name = 'office_frame';
            officeFrame.id = 'office_frame';

            // The title should be set for accessibility
            officeFrame.title = 'Office Frame';

            // This attribute allows true fullscreen mode in slideshow view
            // when using PowerPoint's 'view' action.
            officeFrame.setAttribute('allowfullscreen', 'true');

            // The sandbox attribute is needed to allow automatic redirection to the O365 sign-in page in the business user flow
            officeFrame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-popups allow-top-navigation allow-popups-to-escape-sandbox');
            frameHolder.appendChild(officeFrame);

            const form = document.getElementById('office_form');
            form.action = createActionUrl().toString();
            form.submit();

                    """.trimIndent()
                )
            }
        }
    }
}
