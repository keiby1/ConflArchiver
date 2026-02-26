package com.example.ConflArchReport.Utils;

public class ConstContent {
    public static String getPlaceholderText(String pageTitle, String ticket, String now, String viewURL){
        if (ticket != null && !ticket.isBlank()) {
            return "Заключение " + pageTitle + " по тикету " + ticket + " было архивировано " + now + ". Для просмотра используйте " + viewURL;
        } else {
            return "Заключение " + pageTitle + " было архивировано " + now + ". Для просмотра используйте " + viewURL;
        }
    }
}
