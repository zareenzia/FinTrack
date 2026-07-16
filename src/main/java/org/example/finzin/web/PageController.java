package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index(HttpServletRequest request) {
        if (request.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        if (request.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        return "forward:/login.html";
    }

    @GetMapping("/signup")
    public String signup(HttpServletRequest request) {
        if (request.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        return "forward:/signup.html";
    }

    // Protected routes - require authentication
    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/dashboard.html";
    }

    @GetMapping("/transactions")
    public String transactions(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/transactions.html";
    }

    @GetMapping("/notes")
    public String notes(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/notes.html";
    }

    @GetMapping("/todos")
    public String todos(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/todos.html";
    }

    @GetMapping("/settings")
    public String settings(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/settings.html";
    }

    @GetMapping("/assets")
    public String assets(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/assets.html";
    }

    // Recurring Transactions now lives on the Transactions page.
    @GetMapping("/recurring-transactions")
    public String recurringTransactions() {
        return "redirect:/transactions";
    }

    @GetMapping("/budget-planner")
    public String budgetPlanner(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/budget-planner.html";
    }

    @GetMapping("/financial-planner")
    public String financialPlanner(HttpServletRequest request) {
        if (request.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "forward:/financial-planner.html";
    }
}

