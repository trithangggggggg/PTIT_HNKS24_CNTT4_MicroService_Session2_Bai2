# BÀI TẬP 2: TÁCH DỊCH VỤ THEO PHONG CÁCH SOA VỚI HỢP ĐỒNG DỊCH VỤ RÕ RÀNG

> **Môn học:** Microservice Architecture  
> **Cấp độ:** Vận dụng cơ bản | Session 02: Từ Monolithic đến Microservice  
> **Dự án:** Hệ thống quản trị thư viện số LibraX

---

## 1. Phân tích lỗi toán tử `==` và sửa lỗi `EsbSimulator`

### 1.1. Bản chất lỗi so sánh chuỗi bằng `==` trong Java
Trong ngôn ngữ Java:
- Toán tử `==` thực hiện so sánh **tham chiếu bộ nhớ** (reference equality / memory address), tức là kiểm tra xem 2 biến có trỏ đến cùng một đối tượng ô nhớ trên Heap hay String Constant Pool hay không.
- Phương thức `.equals()` thực hiện so sánh **giá trị nội dung** (content/value equality) của chuỗi ký tự bên trong đối tượng.

**Vì sao `toService == "NotificationService"` gây lỗi:**
- Khi tham số `toService` được truyền vào từ bên ngoài (ví dụ: giải mã từ gói tin SOAP/XML, đọc từ HTTP Header, JSON payload, hoặc được tạo thông qua `new String(...)`, `substring()`, dynamic deserialization...), đối tượng này nằm ở một vùng nhớ riêng biệt trên Heap.
- Literal `"NotificationService"` trong code lại nằm trong String Constant Pool.
- Do đó, dù chuỗi `toService` có nội dung chính xác là `"NotificationService"`, phép so sánh `toService == "NotificationService"` vẫn trả về `false`.
- Hậu quả: Luồng định tuyến không bao giờ nhảy vào khối xử lý mong muốn, tin nhắn bị bỏ qua hoặc rơi vào trạng thái không xác định.

### 1.2. Vấn đề thiếu nhánh `else` (Định tuyến thất bại)
- Đoạn mã ban đầu không có nhánh `else` mặc định. Khi `toService` không khớp với `"NotificationService"` hoặc `"PaymentService"`, thông điệp bị "nuốt chửng" (silent failure) mà không có bất kỳ dòng log hay cảnh báo nào.
- Đội ngũ vận hành sẽ không phát hiện được lỗi cấu hình tên dịch vụ, rớt gói tin hoặc dịch vụ chưa đăng ký.

### 1.3. Mã nguồn `EsbSimulator` hoàn chỉnh sau khi khắc phục

```java
package com.librax.esb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsbSimulator {

    private static final Logger logger = LoggerFactory.getLogger(EsbSimulator.class);

    private final NotificationService notificationService;
    private final PaymentService paymentService;

    public EsbSimulator(NotificationService notificationService, PaymentService paymentService) {
        this.notificationService = notificationService;
        this.paymentService = paymentService;
    }

    /**
     * Định tuyến thông điệp đến service đích dựa trên tên service và thao tác nghiệp vụ.
     *
     * @param toService Tên định danh của service đích
     * @param operation Tên thao tác nghiệp vụ cần thực thi
     * @param payload Dữ liệu thông điệp (XML/JSON)
     */
    public void routeMessage(String toService, String operation, String payload) {
        // Kiểm tra tính hợp lệ của tham số đầu vào
        if (toService == null || toService.trim().isEmpty()) {
            logger.error("[ESB Routing Failed] Tham số 'toService' không hợp lệ (null hoặc rỗng). Payload: {}", payload);
            throw new IllegalArgumentException("Destination service name must not be null or empty");
        }

        // Sử dụng .equalsIgnoreCase() hoặc .equals() để so sánh giá trị chuỗi an toàn
        if ("NotificationService".equalsIgnoreCase(toService.trim())) {
            logger.info("[ESB Routing] Chuyển tiếp thành công tới NotificationService | Operation: {}", operation);
            notificationService.handle(operation, payload);

        } else if ("PaymentService".equalsIgnoreCase(toService.trim())) {
            logger.info("[ESB Routing] Chuyển tiếp thành công tới PaymentService | Operation: {}", operation);
            paymentService.handle(operation, payload);

        } else {
            // Xử lý khi toService không khớp với bất kỳ service nào đã đăng ký
            String errorMsg = String.format(
                "[ESB Routing Rejected] Không tìm thấy service đích phù hợp: '%s' | Operation: '%s' | Payload: %s",
                toService, operation, payload
            );
            logger.warn(errorMsg);
            
            // Xử lý gửi vào Dead Letter Queue (DLQ) hoặc bắn ngoại lệ cảnh báo
            handleUnroutableMessage(toService, operation, payload, errorMsg);
        }
    }

    private void handleUnroutableMessage(String toService, String operation, String payload, String reason) {
        // Ghi nhận cảnh báo và có thể tích hợp lưu vào bảng Dead Letter Queue để kiểm tra lại
        logger.error("[ESB DLQ] Thông điệp đã được chuyển vào Dead Letter Queue để phân tích: {}", reason);
    }
}
```

---

## 2. Hợp đồng dịch vụ (Service Contract — WSDL rút gọn)

Trong kiến trúc SOA, các dịch vụ liên lạc với nhau thông qua hợp đồng được định nghĩa chặt chẽ (Schema validation). Dưới đây là WSDL rút gọn định nghĩa thao tác `notifyOverdue(memberId, bookId, dueDate)`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions name="NotificationServiceDefinition"
    targetNamespace="http://librax.com/services/notification"
    xmlns="http://schemas.xmlsoap.org/wsdl/"
    xmlns:tns="http://librax.com/services/notification"
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/">

    <!-- 1. Data Types & XML Schema Definitions -->
    <types>
        <xsd:schema targetNamespace="http://librax.com/services/notification"
                    elementFormDefault="qualified">
            
            <!-- Request Element -->
            <xsd:element name="NotifyOverdueRequest">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="memberId" type="xsd:string" />
                        <xsd:element name="bookId" type="xsd:string" />
                        <xsd:element name="dueDate" type="xsd:date" />
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>

            <!-- Response Element -->
            <xsd:element name="NotifyOverdueResponse">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="status" type="xsd:string" />
                        <xsd:element name="trackingId" type="xsd:string" />
                        <xsd:element name="sentAt" type="xsd:dateTime" />
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>
        </xsd:schema>
    </types>

    <!-- 2. Message Definitions -->
    <message name="NotifyOverdueInputMessage">
        <part name="parameters" element="tns:NotifyOverdueRequest" />
    </message>

    <message name="NotifyOverdueOutputMessage">
        <part name="parameters" element="tns:NotifyOverdueResponse" />
    </message>

    <!-- 3. PortType (Service Interface) -->
    <portType name="NotificationPortType">
        <operation name="notifyOverdue">
            <documentation>Gửi thông báo nhắc nhở độc giả khi sách đã quá hạn trả</documentation>
            <input message="tns:NotifyOverdueInputMessage" />
            <output message="tns:NotifyOverdueOutputMessage" />
        </operation>
    </portType>

    <!-- 4. Binding (SOAP 1.1 qua giao thức HTTP) -->
    <binding name="NotificationSoapBinding" type="tns:NotificationPortType">
        <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http" />
        <operation name="notifyOverdue">
            <soap:operation soapAction="http://librax.com/services/notification/notifyOverdue" />
            <input>
                <soap:body use="literal" />
            </input>
            <output>
                <soap:body use="literal" />
            </output>
        </operation>
    </binding>

    <!-- 5. Service Endpoint Location -->
    <service name="NotificationService">
        <port name="NotificationSoapPort" binding="tns:NotificationSoapBinding">
            <soap:address location="http://esb.librax.internal/services/NotificationService" />
        </port>
    </service>

</definitions>
```

---

## 3. Luồng đi của thông điệp (Message Flow)

Sau khi `EsbSimulator` được khắc phục, luồng xử lý thông điệp diễn ra tuần tự và an toàn qua các bước sau:

```
+-------------------+         +--------------------+         +-----------------------+
| BorrowingService  |         |   EsbSimulator     |         |  NotificationService  |
+---------+---------+         +---------+----------+         +-----------+-----------+
          |                             |                                |
          | 1. Phát hiện sách quá hạn   |                                |
          |    (memberId, bookId, date) |                                |
          |                             |                                |
          | 2. Đóng gói SOAP/XML payload|                                |
          |    routeMessage(...)        |                                |
          |---------------------------->|                                |
          |                             | 3. Kiểm tra toService          |
          |                             |    (dùng .equalsIgnoreCase)    |
          |                             | 4. Log định tuyến thành công   |
          |                             |                                |
          |                             | 5. Chuyển tiếp handle(...)     |
          |                             |------------------------------->|
          |                             |                                | 6. Parse payload,
          |                             |                                |    lấy thông tin độc giả
          |                             |                                | 7. Gửi Email / SMS
          |                             |                                | 8. Trả về kết quả
          |                             |<-------------------------------|
          | 9. Nhận phản hồi/xác nhận   |                                |
          |<----------------------------|                                |
          |                             |                                |
```

### Giải thích chi tiết các bước:
1. **Phát hiện sự kiện (Detection):** Định kỳ hoặc theo sự kiện, `BorrowingService` quét hệ thống và phát hiện phiếu mượn của độc giả đã vượt quá `dueDate`.
2. **Khởi tạo thông điệp (Message Construction):** `BorrowingService` chuẩn hóa dữ liệu theo cấu trúc của `NotifyOverdueRequest` (`memberId`, `bookId`, `dueDate`) thành định dạng XML payload và gọi `EsbSimulator.routeMessage("NotificationService", "notifyOverdue", payload)`.
3. **Tiếp nhận & Định tuyến tại ESB (Routing Logic):**
   - `EsbSimulator` kiểm tra `toService` bằng phương thức `"NotificationService".equalsIgnoreCase(toService)`.
   - Kết quả trả về `true` (không còn bị lỗi so sánh tham chiếu ô nhớ).
4. **Ghi log giám sát (Audit Logging):** ESB ghi nhận dòng log: `[ESB Routing] Chuyển tiếp thành công tới NotificationService | Operation: notifyOverdue`.
5. **Chuyển giao tới Service đích (Invocation):** `EsbSimulator` gọi `notificationService.handle("notifyOverdue", payload)`.
6. **Xử lý nghiệp vụ tại đích (Processing):** `NotificationService` đọc payload, truy vấn kênh liên lạc của độc giả (Email/SMS) và thực hiện gửi thông báo nhắc trả sách.
7. **Phản hồi (Response):** `NotificationService` trả về kết quả `NotifyOverdueResponse` (`status="SUCCESS"`, `trackingId`, `sentAt`), ESB định tuyến phản hồi ngược lại cho `BorrowingService` để hoàn tất vòng đời thông điệp.
