-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  kind varchar(20) NOT NULL,
  CHECK(kind IN ('STAFF','CUSTOMER')),
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);



CREATE TABLE service_category (
id bigint AUTO_INCREMENT PRIMARY KEY,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE salon_service (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
category_id bigint NOT NULL,
department_id bigint NOT NULL,
price decimal(16,2) NOT NULL,
duration_minutes integer NOT NULL,
buffer_minutes integer NOT NULL,
resource_required boolean NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(category_id) REFERENCES service_category(id),
FOREIGN KEY(department_id) REFERENCES department(id),
UNIQUE(code),
CHECK(price>=0 AND duration_minutes BETWEEN 15 AND 480 AND buffer_minutes BETWEEN 0 AND 120)
);

CREATE TABLE resource (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id),
UNIQUE(code)
);

CREATE TABLE staff_member (
id bigint AUTO_INCREMENT PRIMARY KEY,
account_id bigint NOT NULL,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(account_id) REFERENCES account(id),
FOREIGN KEY(department_id) REFERENCES department(id),
UNIQUE(account_id)
);

CREATE TABLE customer (
id bigint AUTO_INCREMENT PRIMARY KEY,
account_id bigint,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
email varchar(200) NOT NULL,
phone varchar(60) NOT NULL,
note varchar(1000) NOT NULL,
email_consent boolean NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(account_id) REFERENCES account(id),
FOREIGN KEY(department_id) REFERENCES department(id),
UNIQUE(account_id)
);

CREATE TABLE work_shift (
id bigint AUTO_INCREMENT PRIMARY KEY,
staff_id bigint NOT NULL,
weekday integer NOT NULL,
start_minute integer NOT NULL,
end_minute integer NOT NULL,
break_start integer NOT NULL,
break_end integer NOT NULL,
FOREIGN KEY(staff_id) REFERENCES staff_member(id),
UNIQUE(staff_id,weekday),
CHECK(weekday BETWEEN 1 AND 7 AND start_minute>=0 AND end_minute<=1440 AND end_minute>start_minute)
);

CREATE TABLE blocked_time (
id bigint AUTO_INCREMENT PRIMARY KEY,
department_id bigint NOT NULL,
staff_id bigint,
resource_id bigint,
starts_at timestamp(6) NOT NULL,
ends_at timestamp(6) NOT NULL,
reason varchar(1000) NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(staff_id) REFERENCES staff_member(id),
FOREIGN KEY(resource_id) REFERENCES resource(id)
);

CREATE TABLE appointment (
id bigint AUTO_INCREMENT PRIMARY KEY,
number varchar(60) NOT NULL,
department_id bigint NOT NULL,
customer_id bigint NOT NULL,
staff_id bigint NOT NULL,
service_id bigint NOT NULL,
resource_id bigint,
customer_name varchar(120) NOT NULL,
staff_name varchar(120) NOT NULL,
service_name varchar(120) NOT NULL,
resource_name varchar(120) NOT NULL,
starts_at timestamp(6) NOT NULL,
ends_at timestamp(6) NOT NULL,
blocked_until timestamp(6) NOT NULL,
duration_minutes integer NOT NULL,
buffer_minutes integer NOT NULL,
price decimal(16,2) NOT NULL,
discount decimal(16,2) NOT NULL,
total decimal(16,2) NOT NULL,
paid decimal(16,2) NOT NULL,
refunded decimal(16,2) NOT NULL,
status varchar(30) NOT NULL,
revision bigint NOT NULL,
note varchar(1000) NOT NULL,
service_note varchar(1000) NOT NULL,
checked_out boolean NOT NULL,
created_at timestamp(6) NOT NULL,
updated_at timestamp(6) NOT NULL,
service_started_at timestamp(6),
service_finished_at timestamp(6),
completed_at timestamp(6),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(staff_id) REFERENCES staff_member(id),
FOREIGN KEY(service_id) REFERENCES salon_service(id),
FOREIGN KEY(resource_id) REFERENCES resource(id),
UNIQUE(number),
CHECK(price>=0 AND discount>=0 AND total>=0 AND paid>=0 AND refunded>=0 AND refunded<=paid AND revision>=0)
);

CREATE TABLE payment_entry (
id bigint AUTO_INCREMENT PRIMARY KEY,
appointment_id bigint NOT NULL,
department_id bigint NOT NULL,
source_id bigint,
kind varchar(20) NOT NULL,
amount decimal(16,2) NOT NULL,
method varchar(60) NOT NULL,
reference varchar(200) NOT NULL,
reason varchar(1000) NOT NULL,
created_at timestamp(6) NOT NULL,
actor_id bigint NOT NULL,
FOREIGN KEY(appointment_id) REFERENCES appointment(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(source_id) REFERENCES payment_entry(id),
FOREIGN KEY(actor_id) REFERENCES account(id),
CHECK(amount>0)
);

CREATE TABLE booking_event (
id bigint AUTO_INCREMENT PRIMARY KEY,
appointment_id bigint NOT NULL,
kind varchar(30) NOT NULL,
note varchar(2000) NOT NULL,
actor_id bigint NOT NULL,
created_at timestamp(6) NOT NULL,
FOREIGN KEY(appointment_id) REFERENCES appointment(id),
FOREIGN KEY(actor_id) REFERENCES account(id)
);

CREATE TABLE message_job (
id bigint AUTO_INCREMENT PRIMARY KEY,
appointment_id bigint NOT NULL,
kind varchar(30) NOT NULL,
revision bigint NOT NULL,
due_at timestamp(6) NOT NULL,
status varchar(30) NOT NULL,
attempts integer NOT NULL,
result_code varchar(60) NOT NULL,
sent_at timestamp(6),
FOREIGN KEY(appointment_id) REFERENCES appointment(id)
);
CREATE TABLE staff_service(staff_id bigint NOT NULL,service_id bigint NOT NULL,PRIMARY KEY(staff_id,service_id),FOREIGN KEY(staff_id) REFERENCES staff_member(id),FOREIGN KEY(service_id) REFERENCES salon_service(id));
CREATE INDEX ix_appointment_staff ON appointment(staff_id,starts_at,blocked_until,status);
CREATE INDEX ix_appointment_customer ON appointment(customer_id,starts_at,status);
CREATE INDEX ix_appointment_resource ON appointment(resource_id,starts_at,blocked_until,status);
CREATE INDEX ix_payment_booking ON payment_entry(appointment_id,created_at);
CREATE INDEX ix_message_due ON message_job(status,due_at);

CREATE TABLE mutation_stamp (id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(80) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint);
