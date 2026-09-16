# Báo Cáo Bài Tập 2: Chuyển Đổi RestTemplate sang FeignClient

## 1. So sánh số dòng code

- **ProductServiceClientRT (RestTemplate)**: ~25-30 dòng code (bao gồm constructor, try-catch blocks để handle lỗi thủ công).
- **ProductClient (FeignClient)**: ~10 dòng code (chỉ cần khai báo interface và các annotation). Thêm class FallbackFactory khoảng 20 dòng.

## 2. Phân tích ưu/nhược điểm

### RestTemplate
- **Ưu điểm**:
  - Code tường minh, lập trình viên có thể kiểm soát chi tiết từng request, cách bắt exception, cấu hình tham số.
  - Phù hợp với các hệ thống không sử dụng Spring Cloud hoặc có nhu cầu gọi HTTP động, không cần khai báo interface cố định.
- **Nhược điểm**:
  - Boilerplate code nhiều (try-catch, URL hardcode hoặc ghép chuỗi).
  - Code khó đọc, bảo trì khó khăn khi số lượng API endpoint lớn.

### FeignClient
- **Ưu điểm**:
  - Declarative (Khai báo): Cách tiếp cận hướng interface, chỉ cần quan tâm "gọi cái gì" (thông qua Annotation) thay vì "gọi như thế nào". Code ngắn gọn, dễ đọc và tập trung vào nghiệp vụ.
  - Tích hợp sẵn và dễ dàng cấu hình Load Balancing, Circuit Breaker qua properties/yaml.
  - Phân tách riêng biệt logic fallback (FallbackFactory) giúp code interface chính sạch sẽ.
- **Nhược điểm**:
  - Cần phải học cách sử dụng các Annotation của Spring Cloud OpenFeign.
  - Khó tuỳ biến những logic quá phức tạp, dynamic URL ở mức độ sâu so với RestTemplate.

## 3. Khi nào dùng mỗi cách?
- **Dùng FeignClient**: Khi làm việc trong hệ sinh thái Spring Cloud, kiến trúc Microservices có Service Registry rõ ràng. Khi muốn chuẩn hóa cách giao tiếp giữa các services nội bộ thông qua Interface để dễ dàng quản lý.
- **Dùng RestTemplate (hoặc WebClient)**: Khi gọi tới các external API (bên thứ ba) không nằm trong Service Registry, hoặc khi cần một cách gọi HTTP client cấp thấp, cấu hình request động linh hoạt. Tuy nhiên hiện tại Spring khuyến khích chuyển sang WebClient hoặc RestClient.
