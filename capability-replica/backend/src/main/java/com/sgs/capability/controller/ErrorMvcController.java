package com.sgs.capability.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Copies Web.Core ErrorController routes and hands them to React exception pages. */
@Controller
public class ErrorMvcController {

    @GetMapping({"/Error", "/Error/Index"})
    public String index(@RequestParam(name = "statusCode", defaultValue = "0") int statusCode) {
        if (statusCode == 404) {
            return e404();
        }
        if (statusCode == 403) {
            return e403();
        }
        // 原 Index 默认渲染通用 Error 视图，这里映射到 React 500 异常页。
        return "redirect:/exception/500";
    }

    @GetMapping("/Error/E403")
    public String e403() {
        return "redirect:/exception/403";
    }

    @GetMapping("/Error/E404")
    public String e404() {
        return "redirect:/exception/404";
    }
}
