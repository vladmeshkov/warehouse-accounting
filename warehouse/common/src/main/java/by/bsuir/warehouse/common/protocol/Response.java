package by.bsuir.warehouse.common.protocol;

import java.io.Serializable;

public class Response implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Serializable data;
    private final String token;

    // Для передачи файлов
    private final byte[] fileData;
    private final String fileName;

    private Response(boolean success, String message, Serializable data, String token,
                     byte[] fileData, String fileName) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.token = token;
        this.fileData = fileData;
        this.fileName = fileName;
    }

    public static Response ok(Serializable data) {
        return new Response(true, "OK", data, null, null, null);
    }

    public static Response ok(String message) {
        return new Response(true, message, null, null, null, null);
    }

    public static Response ok(String message, Serializable data) {
        return new Response(true, message, data, null, null, null);
    }

    public static Response withToken(String token, Serializable data) {
        return new Response(true, "Авторизация успешна", data, token, null, null);
    }

    public static Response error(String message) {
        return new Response(false, message, null, null, null, null);
    }

    /** Фабричный метод для отправки файла клиенту */
    public static Response okFile(byte[] fileData, String fileName) {
        return new Response(true, "OK", null, null, fileData, fileName);
    }

    public boolean isSuccess()      { return success; }
    public String getMessage()      { return message; }
    public Serializable getData()   { return data; }
    public String getToken()        { return token; }
    public byte[] getFileData()     { return fileData; }
    public String getFileName()     { return fileName; }
}