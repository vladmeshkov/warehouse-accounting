package by.bsuir.warehouse.server.network;

import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Обработчик одного клиентского подключения.
 * Запускается в отдельном потоке из пула ThreadPoolExecutor.
 * Читает Request, передаёт в RequestDispatcher, отправляет Response.
 * При разрыве соединения корректно освобождает ресурсы.
 */
public class ClientHandler implements Runnable {

    private static final Logger log = Logger.getLogger(ClientHandler.class.getName());

    private final Socket           socket;
    private final RequestDispatcher dispatcher;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket     = socket;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        String clientAddr = socket.getRemoteSocketAddress().toString();
        log.info("Клиент подключился: " + clientAddr);

        try (ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            // Обслуживаем клиента в цикле — один поток на все запросы сессии
            while (!socket.isClosed()) {
                Request request;
                try {
                    request = (Request) in.readObject();
                } catch (EOFException | java.net.SocketException e) {
                    // Клиент закрыл соединение — нормальное завершение
                    break;
                }

                log.fine("Запрос от " + clientAddr + ": " + request.getAction());
                Response response = dispatcher.dispatch(request);
                out.writeObject(response);
                out.flush();
                out.reset(); // сбрасываем кэш ObjectOutputStream для следующего запроса
            }

        } catch (ClassNotFoundException e) {
            log.warning("Неизвестный класс в запросе от " + clientAddr + ": " + e.getMessage());
        } catch (IOException e) {
            log.warning("Ошибка соединения с " + clientAddr + ": " + e.getMessage());
        } finally {
            closeSocket();
            log.info("Клиент отключился: " + clientAddr);
        }
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            log.warning("Ошибка закрытия сокета: " + e.getMessage());
        }
    }
}
