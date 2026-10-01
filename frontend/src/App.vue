<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, onMounted, ref } from "vue";
import {
  CalendarDays,
  Users,
  Scissors,
  Settings,
  ChartNoAxesColumn,
  Clock3,
  LogOut,
  Plus,
  ChevronLeft,
  ChevronRight,
  X,
  Menu,
  RefreshCw,
  Printer,
  Download,
  Search,
  ArrowUpRight,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport } from "./api.js";
import { money, minutes, localDate, localTime, localInput } from "./format.js";
const language = ref(localStorage.getItem("salonflow.language") || "zh"),
  profile = ref(null),
  catalog = ref({}),
  publicCatalog = ref({}),
  page = ref("dashboard"),
  rows = ref([]),
  total = ref(0),
  pagination = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("id"),
  descending = ref(true),
  date = ref(""),
  error = ref(""),
  notice = ref(""),
  busy = ref(false),
  loading = ref(false),
  sidebar = ref(false),
  dialog = ref(null),
  form = ref({}),
  detail = ref(null),
  overview = ref({ today: [], upcoming: [], counts: {} }),
  report = ref({ items: [] }),
  reportFrom = ref(""),
  reportTo = ref(""),
  viewMode = ref("calendar"),
  login = ref({ username: "", password: "" }),
  signup = ref(false),
  register = ref({
    name: "",
    username: "",
    password: "",
    departmentId: "",
    email: "",
    phone: "",
    emailConsent: false,
  }),
  booking = ref({
    serviceId: "",
    staffId: "",
    resourceId: "",
    customerId: "",
    date: "",
    startsAt: "",
    note: "",
  }),
  available = ref([]),
  slotLoading = ref(false),
  pendingBooking = ref(null);
let slotQuery = 0;
let loadQuery = 0;
const t = (zh, en) => (language.value === "en" ? en : zh);
const can = (p) => profile.value?.permissions.includes(p);
const isCustomer = computed(() => profile.value?.kind === "CUSTOMER");
const setting = (k) =>
  catalog.value.settings?.find((x) => x.code === k)?.value ||
  publicCatalog.value[k];
const zone = computed(() => setting("timezone") || "Asia/Shanghai"),
  currency = computed(() => setting("currency") || "CNY");
const fmt = (v) => money(v, currency.value, language.value),
  when = (v) => localTime(v, zone.value, language.value);
const labels = {
  CONFIRMED: ["已预约", "Booked"],
  ARRIVED: ["已到店", "Arrived"],
  IN_SERVICE: ["服务中", "In service"],
  SERVED: ["待结账", "Checkout due"],
  COMPLETED: ["已结账", "Completed"],
  CANCELLED: ["已取消", "Cancelled"],
  NO_SHOW: ["未到店", "No-show"],
  PAYMENT: ["收款", "Payment"],
  REFUND: ["退款", "Refund"],
  ALL: ["全部门店", "All departments"],
  DEPARTMENT: ["本门店", "Own department"],
  ASSIGNED: ["本人关联", "Assigned / own"],
  QUEUED: ["待发送", "Queued"],
  NOT_CONFIGURED: ["未配置邮件", "SMTP not configured"],
  SENT: ["SMTP已接受", "SMTP accepted"],
  SKIPPED: ["不发送", "Skipped"],
  FAILED: ["发送失败", "Failed"],
  CONFIRM: ["预约确认", "Confirmation"],
  RESCHEDULE: ["改期", "Rescheduled"],
  CANCEL: ["取消通知", "Cancellation"],
  REMINDER: ["提前24小时提醒", "24h reminder"],
};
const label = (v) => (labels[v] ? t(...labels[v]) : v);
const errors = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Please sign in again"],
  LOGIN_FAILED: ["账号或密码错误", "Invalid credentials"],
  FORBIDDEN: ["没有操作权限", "Permission denied"],
  OUT_OF_SCOPE: ["无权访问此记录", "Record is outside your scope"],
  INVALID_INPUT: ["请检查表单输入", "Check the form values"],
  CONFLICT: ["编码重复或记录已被引用", "Duplicate or referenced record"],
  WEAK_PASSWORD: [
    "密码需12–72位，含大小写字母和数字",
    "Use 12–72 characters with uppercase, lowercase and digits",
  ],
  INVALID_USERNAME: [
    "账号用3–60位字母、数字、下划线或点",
    "Use 3–60 letters, numbers, underscores or dots",
  ],
  SLOT_TAKEN: [
    "这个时段已被占用，请重新选择",
    "This slot is no longer available",
  ],
  OUTSIDE_SHIFT: ["不在员工工作时段内", "Outside staff working hours"],
  STAFF_BREAK: ["包含员工休息时间", "Overlaps a staff break"],
  BLOCKED_TIME: ["员工请假或资源暂停", "Staff or resource is unavailable"],
  STAFF_SERVICE_UNAVAILABLE: [
    "员工不可预约此项目",
    "This staff member cannot provide this service",
  ],
  BOOKING_WINDOW: [
    "请选择未来90天内的可用时段",
    "Choose an available slot within 90 days",
  ],
  CANCELLATION_NOTICE: [
    "已超过自助改期或取消时限，请联系门店",
    "Contact the salon to change this appointment",
  ],
  RESOURCE_REQUIRED: [
    "此项目需要选择座位或房间",
    "Select a seat or room for this service",
  ],
  INVALID_RESOURCE: ["座位或房间不可用", "Resource is unavailable"],
  INVALID_CUSTOMER: ["客户档案不可用", "Client record is unavailable"],
  INVALID_STYLIST: [
    "请选择同店启用的服务员工账号",
    "Select an enabled stylist account in this salon",
  ],
  INVALID_SERVICES: ["请选择员工可提供的项目", "Choose eligible services"],
  STALE_REVISION: [
    "记录已改变，请刷新后重试",
    "This record changed; refresh and try again",
  ],
  INVALID_STATE: [
    "当前状态不能执行此操作",
    "Action is not available in this state",
  ],
  NOT_ASSIGNED_STYLIST: [
    "只能由本单服务员工执行",
    "Only the assigned stylist can perform this service",
  ],
  SERVICE_IN_PROGRESS: [
    "员工或资源仍在服务其他客户",
    "Staff or resource is still serving another client",
  ],
  TOO_EARLY: ["还未到接待或服务时间", "Too early for this action"],
  NOT_DUE: ["预约尚未结束，不能标记未到店", "The appointment has not ended"],
  APPOINTMENT_EXPIRED: [
    "预约已过期，请改期或登记未到店",
    "Reschedule or record a no-show",
  ],
  SERVICE_NOTE_REQUIRED: [
    "请填写服务完成记录",
    "Enter the service completion note",
  ],
  REASON_REQUIRED: ["请填写原因", "A reason is required"],
  PAYMENT_EXCEEDS_DUE: [
    "收款不能超过剩余应收",
    "Payment exceeds the outstanding balance",
  ],
  REFUND_EXCEEDS_PAYMENT: [
    "退款不能超过原收款剩余金额",
    "Refund exceeds the original payment balance",
  ],
  DISCOUNT_EXCEEDS_PRICE: [
    "折扣金额不能超过项目原价",
    "Discount exceeds the original price",
  ],
  CHECKOUT_REQUIRED: ["请先核对账单", "Confirm the bill first"],
  ALREADY_CHECKED_OUT: ["账单已确认", "Bill already confirmed"],
  INVALID_MONEY: [
    "金额需非负且最多两位小数",
    "Use a nonnegative amount with at most two decimals",
  ],
  INVALID_PAYMENT_METHOD: [
    "请选择启用的收款方式",
    "Select an enabled payment method",
  ],
  ACTIVE_APPOINTMENTS: [
    "已有预约，请先改期或重新分配",
    "Existing appointments must be moved first",
  ],
  ACCOUNT_LINKED: [
    "账号已关联客户或员工，不能改换身份",
    "This account is linked to a client or staff record",
  ],
  CUSTOMER_ROLE_LOCKED: [
    "客户角色为固定的本人范围",
    "Customer access is fixed to their own records",
  ],
  LAST_ADMIN: [
    "必须保留一位启用的系统管理员",
    "Keep an enabled system administrator",
  ],
  CURRENCY_LOCKED: [
    "已有预约，不能改币种",
    "Currency is locked after the first booking",
  ],
  TIMEZONE_LOCKED: [
    "已有预约，不能改时区",
    "Timezone is locked after the first booking",
  ],
  REGISTRATION_DISABLED: [
    "门店未开放自助注册",
    "Self-registration is disabled",
  ],
  REGISTRATION_THROTTLED: [
    "注册频率过高，请稍后重试",
    "Too many registration attempts",
  ],
  LOGIN_THROTTLED: ["登录尝试过多，请稍后重试", "Too many login attempts"],
  INVALID_SHIFT: ["请检查工作和休息时间", "Check working and break times"],
  INVALID_EMAIL: ["邮件地址格式不正确", "Invalid email address"],
  AMBIGUOUS_LOCAL_TIME: [
    "此时间处于夏令时切换，请选择其他时段",
    "Choose a time outside the DST transition",
  ],
  CHOOSE_STAFF_OR_RESOURCE: [
    "只选择员工或资源其中一个",
    "Select either staff or resource",
  ],
  BUILTIN_RESOURCE: [
    "不能删除系统内建资源",
    "Built-in resources cannot be deleted",
  ],
  INVALID_PAGE: ["筛选条件不正确", "Invalid filter"],
  INVALID_DATE: [
    "日期范围不正确，最多366天",
    "Use a valid date range of up to 366 days",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确", "Incorrect current password"],
};
function fail(e) {
  error.value = errors[e.message]
    ? t(...errors[e.message])
    : t(
        "操作失败，请检查输入或稍后重试",
        "Operation failed; check your input and try again",
      );
  if (e.message === "UNAUTHENTICATED") {
    profile.value = null;
    resetCsrf();
  }
}
function toggleLanguage() {
  language.value = language.value === "zh" ? "en" : "zh";
  localStorage.setItem("salonflow.language", language.value);
}
const title = computed(
  () =>
    profile.value?.menus.find((m) => m.code === page.value)?.[
      language.value === "en" ? "nameEn" : "name"
    ] || t("在线预约", "Book online"),
);
const selectedService = computed(() =>
  catalog.value.services?.find((s) => s.id === booking.value.serviceId),
);
const eligibleStaff = computed(() =>
  (catalog.value.staff || []).filter(
    (s) => s.enabled && s.services.includes(booking.value.serviceId),
  ),
);
const eligibleResources = computed(() =>
  (catalog.value.resources || []).filter(
    (r) => r.enabled && r.departmentId === selectedService.value?.departmentId,
  ),
);
const calendarStaff = computed(() =>
  (catalog.value.staff || []).filter(
    (s) => rows.value.some((o) => o.staffId === s.id) || s.enabled,
  ),
);
const calendarJobs = (id) =>
  rows.value
    .filter((o) => o.staffId === id)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
const activeCount = (key) => overview.value.counts?.[key] || 0;
const adminPages = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const masters = [
  "services",
  "categories",
  "staff",
  "resources",
  "customers",
  "shifts",
  "blocks",
];
const F = (name, zh, en, type = "text", options = [], extra = {}) => ({
  name,
  zh,
  en,
  type,
  options,
  ...extra,
});
const opts = (xs, field = "name") =>
  (xs || []).map((x) => ({ value: x.id, label: x[field] || String(x.id) }));
const departmentOpts = () => opts(catalog.value.departments);
function dialogForm(
  zh,
  en,
  fields,
  values,
  path,
  method = "POST",
  kind = "form",
) {
  error.value = "";
  form.value = { ...values };
  dialog.value = { zh, en, fields, path, method, kind };
}
async function refresh() {
  try {
    profile.value = await api("/auth/me");
    catalog.value = await api("/catalog");
    if (!date.value) date.value = localDate(new Date(), zone.value);
    if (!reportFrom.value) {
      reportFrom.value = date.value;
      reportTo.value = date.value;
    }
    if (!profile.value.menus.some((m) => m.code === page.value)) {
      page.value = profile.value.menus[0]?.code || "dashboard";
      detail.value = null;
    }
    await load();
  } catch (e) {
    fail(e);
  }
}
async function load() {
  const query = ++loadQuery;
  const target = page.value;
  loading.value = true;
  error.value = "";
  try {
    if (target === "dashboard") {
      const data = await api("/dashboard");
      if (query === loadQuery) overview.value = data;
    } else if (target === "reports") {
      const data = await api(
        "/reports?" +
          new URLSearchParams({ from: reportFrom.value, to: reportTo.value }),
      );
      if (query === loadQuery) report.value = data;
    } else if (target === "book") {
      booking.value.date ||= date.value;
    } else {
      const params = new URLSearchParams({
        search: search.value,
        status: status.value,
        sort: sort.value,
        desc: descending.value,
        page: pagination.value,
        size:
          page.value === "appointments" && viewMode.value === "calendar"
            ? 100
            : 20,
      });
      if (
        page.value === "appointments" &&
        !isCustomer.value &&
        viewMode.value === "calendar"
      )
        params.set("date", date.value);
      const data = await api("/lists/" + page.value + "?" + params);
      if (query === loadQuery) {
        rows.value = data.items;
        total.value = data.total;
      }
    }
  } catch (e) {
    if (query === loadQuery) fail(e);
  } finally {
    if (query === loadQuery) loading.value = false;
  }
}
async function navigate(next) {
  page.value = next;
  pagination.value = 0;
  search.value = "";
  status.value = "";
  detail.value = null;
  sidebar.value = false;
  notice.value = "";
  await load();
}
async function signIn() {
  busy.value = true;
  error.value = "";
  try {
    profile.value = await api("/auth/login", "POST", login.value);
    resetCsrf();
    login.value.password = "";
    page.value = "dashboard";
    await refresh();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function signUp() {
  busy.value = true;
  error.value = "";
  try {
    await api("/public/register", "POST", register.value);
    login.value.username = register.value.username;
    register.value.password = "";
    signup.value = false;
    notice.value = t("注册成功，请登录", "Account created. Please sign in.");
    resetCsrf();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function signOut() {
  if (busy.value) return;
  busy.value = true;
  try {
    await api("/auth/logout", "POST");
    profile.value = null;
    dialog.value = null;
    detail.value = null;
    resetCsrf();
    publicCatalog.value = await api("/public/catalog");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function openAppointment(id) {
  try {
    detail.value = await api("/appointments/" + id);
    error.value = "";
  } catch (e) {
    fail(e);
  }
}
async function fetchSlots() {
  const query = ++slotQuery;
  available.value = [];
  booking.value.startsAt = "";
  slotLoading.value = true;
  error.value = "";
  try {
    const b = booking.value;
    if (!b.serviceId || !b.staffId || !b.date) return;
    const p = new URLSearchParams({
      serviceId: b.serviceId,
      staffId: b.staffId,
      date: b.date,
    });
    if (b.resourceId) p.set("resourceId", b.resourceId);
    if (dialog.value?.appointment)
      p.set("appointmentId", dialog.value.appointment.id);
    const data = await api("/slots?" + p);
    if (query === slotQuery) available.value = data;
  } catch (e) {
    if (query === slotQuery) fail(e);
  } finally {
    if (query === slotQuery) slotLoading.value = false;
  }
}
function beginBooking() {
  slotQuery++;
  slotLoading.value = false;
  pendingBooking.value = null;
  booking.value = {
    serviceId: "",
    staffId: "",
    resourceId: "",
    customerId: "",
    date: date.value,
    startsAt: "",
    note: "",
  };
  available.value = [];
  dialog.value = {
    zh: "新增预约",
    en: "New appointment",
    kind: "booking",
    fields: [],
  };
  error.value = "";
}
async function bookNow() {
  busy.value = true;
  error.value = "";
  try {
    const v = {
      ...booking.value,
      requestKey:
        dialog.value?.requestKey ||
        pendingBooking.value?.key ||
        crypto.randomUUID(),
    };
    const content = JSON.stringify(booking.value);
    if (pendingBooking.value && pendingBooking.value.content !== content)
      v.requestKey = crypto.randomUUID();
    pendingBooking.value = { content, key: v.requestKey };
    if (dialog.value) dialog.value.requestKey = v.requestKey;
    const existing = dialog.value?.appointment;
    let result;
    if (existing) {
      await api("/appointments/" + existing.id + "/reschedule", "POST", {
        ...v,
        revision: existing.revision,
      });
      result = existing;
    } else result = await api("/appointments", "POST", v);
    dialog.value = null;
    pendingBooking.value = null;
    booking.value.startsAt = "";
    available.value = [];
    await refresh();
    if (page.value !== "appointments") await navigate("appointments");
    await openAppointment(result.id);
    notice.value = t("预约已保存", "Appointment saved");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function reschedule(o) {
  slotQuery++;
  slotLoading.value = false;
  pendingBooking.value = null;
  booking.value = {
    serviceId: o.serviceId,
    staffId: o.staffId,
    resourceId: o.resourceId || "",
    customerId: o.customerId,
    date: localDate(o.startsAt, zone.value),
    startsAt: "",
    note: "",
  };
  available.value = [];
  dialog.value = {
    zh: "预约改期",
    en: "Reschedule",
    kind: "booking",
    fields: [],
    appointment: o,
  };
  error.value = "";
}
function editMaster(row) {
  const type = page.value;
  let fields = [];
  const base = { departmentId: profile.value.departmentId, enabled: true };
  if (type === "categories")
    fields = [
      F("name", "分类名称", "Category name"),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  if (type === "services")
    fields = [
      F("code", "项目编码", "Code"),
      F("name", "项目名称", "Service name"),
      F(
        "categoryId",
        "项目分类",
        "Category",
        "select",
        opts(catalog.value.categories),
      ),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F("price", "价格", "Price", "number", [], { min: 0, step: 0.01 }),
      F(
        "durationMinutes",
        "服务时长（分钟）",
        "Duration (minutes)",
        "number",
        [],
        { min: 15, max: 480, step: 1 },
      ),
      F(
        "bufferMinutes",
        "结束后缓冲（分钟）",
        "Cleanup buffer (minutes)",
        "number",
        [],
        { min: 0, max: 120, step: 1 },
      ),
      F(
        "resourceRequired",
        "需要座位或房间",
        "Seat / room required",
        "checkbox",
      ),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  if (type === "resources")
    fields = [
      F("code", "资源编码", "Code"),
      F("name", "资源名称", "Resource name"),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  if (type === "staff")
    fields = [
      F(
        "accountId",
        "服务员工账号",
        "Stylist account",
        "select",
        opts(
          (catalog.value.accounts || []).filter((a) => a.enabled),
          "displayName",
        ),
      ),
      F("name", "对客姓名", "Display name"),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F(
        "services",
        "可做项目",
        "Eligible services",
        "checks",
        opts(catalog.value.services),
      ),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  if (type === "customers")
    fields = [
      F("name", "客户姓名", "Client name"),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F("phone", "联系电话", "Phone", "text", [], { required: false }),
      F("email", "邮箱", "Email", "email", [], {
        required: false,
        maxlength: 200,
      }),
      F("note", "备注", "Notes", "textarea", [], {
        required: false,
        maxlength: 1000,
      }),
      F(
        "emailConsent",
        "同意接收预约邮件",
        "Consent to booking emails",
        "checkbox",
      ),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  if (type === "shifts")
    fields = [
      F("staffId", "员工", "Staff", "select", opts(catalog.value.staff)),
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F(
        "weekday",
        "星期",
        "Weekday",
        "select",
        ["周一", "周二", "周三", "周四", "周五", "周六", "周日"].map(
          (s, i) => ({
            value: i + 1,
            label: t(s, ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][i]),
          }),
        ),
      ),
      F("startMinute", "上班时间", "Shift start", "time"),
      F(
        "endMinute",
        "下班时间（支持24:00）",
        "Shift end (24:00 supported)",
        "text",
        [],
        { pattern: "(?:[01][0-9]|2[0-3]):[0-5][0-9]|24:00", maxlength: 5 },
      ),
      F(
        "breakStart",
        "休息开始（可留空）",
        "Break start (optional)",
        "time",
        [],
        { required: false },
      ),
      F("breakEnd", "休息结束（可留空）", "Break end (optional)", "time", [], {
        required: false,
      }),
    ];
  if (type === "blocks")
    fields = [
      F("departmentId", "门店", "Salon", "select", departmentOpts()),
      F(
        "staffId",
        "请假员工（选其一）",
        "Staff (choose one)",
        "select",
        opts(catalog.value.staff),
        { required: false },
      ),
      F(
        "resourceId",
        "暂停资源（选其一）",
        "Resource (choose one)",
        "select",
        opts(catalog.value.resources),
        { required: false },
      ),
      F("startsLocal", "开始时间", "Start time", "datetime-local"),
      F("endsLocal", "结束时间", "End time", "datetime-local"),
      F("reason", "原因", "Reason", "textarea", [], { maxlength: 1000 }),
    ];
  const values = {
    ...base,
    price: "0",
    durationMinutes: 30,
    bufferMinutes: 0,
    services: [],
    weekday: 1,
    startMinute: "09:00",
    endMinute: "18:00",
    breakStart: "",
    breakEnd: "",
    ...row,
  };
  if (row && type === "blocks") {
    values.startsLocal = localInput(row.startsAt, zone.value);
    values.endsLocal = localInput(row.endsAt, zone.value);
  }
  if (row && type === "shifts")
    for (const key of ["startMinute", "endMinute", "breakStart", "breakEnd"])
      values[key] =
        row[key] === 0 && key.startsWith("break") ? "" : minutes(row[key]);
  dialogForm(
    row ? "编辑" : "新增",
    row ? "Edit" : "Add",
    fields,
    values,
    "/master/" + type + (row ? "/" + row.id : ""),
    row ? "PUT" : "POST",
    type,
  );
}
function editAdmin(row) {
  const type = page.value;
  let fields = [];
  const values = {
    enabled: true,
    scope: "DEPARTMENT",
    permissions: [],
    departmentId: profile.value.departmentId,
    ...row,
    password: "",
  };
  if (type === "users")
    fields = [
      F("username", "登录账号", "Username"),
      F("displayName", "姓名", "Display name"),
      F(
        "password",
        row ? "新密码（留空不变）" : "密码",
        "Password",
        "password",
        [],
        { required: !row, minlength: row ? undefined : 12, maxlength: 72 },
      ),
      F("roleId", "角色", "Role", "select", opts(catalog.value.roles)),
      F("departmentId", "门店部门", "Department", "select", departmentOpts()),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "roles")
    fields = [
      F("name", "角色名称", "Role name"),
      F(
        "scope",
        "数据范围",
        "Data scope",
        "select",
        ["ALL", "DEPARTMENT", "ASSIGNED"].map((x) => ({
          value: x,
          label: label(x),
        })),
      ),
      F(
        "permissions",
        "操作权限",
        "Permissions",
        "checks",
        (catalog.value.permissions || []).map((x) => ({
          value: x.code,
          label: x.name,
        })),
      ),
    ];
  else if (type === "menus")
    fields = [
      F("name", "中文名称", "Chinese label"),
      F("nameEn", "英文名称", "English label"),
      F(
        "permissionCode",
        "所需权限",
        "Required permission",
        "select",
        (catalog.value.permissions || []).map((x) => ({
          value: x.code,
          label: x.name,
        })),
      ),
      F("position", "顺序", "Position", "number", [], { min: 0, step: 1 }),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "dictionaries")
    fields = [
      F("type", "字典类型", "Dictionary type"),
      F("code", "编码", "Code"),
      F("name", "中文名称", "Chinese label"),
      F("nameEn", "英文名称", "English label"),
      F("enabled", "启用", "Enabled", "checkbox"),
    ];
  else if (type === "settings")
    fields = [F("value", "参数值", "Value", "text", [], { maxlength: 200 })];
  else fields = [F("name", "名称", "Name")];
  dialogForm(
    row ? "编辑" : "新增",
    row ? "Edit" : "Add",
    fields,
    values,
    "/admin/" + type + (row ? "/" + row.id : ""),
    row ? "PUT" : "POST",
  );
}
function stateAction(action) {
  const o = detail.value.appointment;
  let fields = [];
  if (["cancel", "no-show", "finish"].includes(action))
    fields = [
      F(
        "note",
        action === "finish" ? "服务记录" : "原因",
        action === "finish" ? "Service note" : "Reason",
        "textarea",
        [],
        { maxlength: 1000 },
      ),
    ];
  if (action === "reassign")
    fields = [
      F(
        "staffId",
        "新的服务员工",
        "New stylist",
        "select",
        opts(
          catalog.value.staff.filter(
            (s) => s.enabled && s.services.includes(o.serviceId),
          ),
        ),
      ),
      F("note", "调整原因", "Reason", "textarea", [], { maxlength: 1000 }),
    ];
  if (action === "checkout")
    fields = [
      F("discount", "折扣金额", "Discount amount", "number", [], {
        min: 0,
        max: o.price,
        step: 0.01,
      }),
      F("note", "折扣原因", "Discount reason", "textarea", [], {
        required: false,
        maxlength: 1000,
      }),
    ];
  if (action === "pay")
    fields = [
      F("amount", "收款金额", "Amount", "number", [], {
        min: 0.01,
        max: Number(o.total) - Number(o.paid),
        step: 0.01,
      }),
      F(
        "method",
        "收款方式",
        "Method",
        "select",
        (catalog.value.dictionaries || [])
          .filter((d) => d.type === "payment_method" && d.enabled)
          .map((d) => ({ value: d.code, label: t(d.name, d.nameEn) })),
      ),
      F("reference", "实际收款凭证号", "Actual payment reference", "text", [], {
        maxlength: 200,
      }),
    ];
  dialogForm(
    labelAction(action),
    labelAction(action),
    fields,
    {
      revision: o.revision,
      requestKey: crypto.randomUUID(),
      discount: "0",
      amount: (Number(o.total) - Number(o.paid)).toFixed(2),
    },
    "/appointments/" + o.id + "/" + action,
  );
}
function refundPayment(p) {
  const used = detail.value.payments
    .filter((r) => r.sourceId === p.id)
    .reduce((n, r) => n + Number(r.amount), 0);
  dialogForm(
    "原单退款",
    "Refund original payment",
    [
      F("amount", "退款金额", "Refund amount", "number", [], {
        min: 0.01,
        max: Number(p.amount) - used,
        step: 0.01,
      }),
      F("reference", "实际退款凭证号", "Actual refund reference", "text", [], {
        maxlength: 200,
      }),
      F("note", "退款原因", "Reason", "textarea", [], { maxlength: 1000 }),
    ],
    {
      revision: detail.value.appointment.revision,
      requestKey: crypto.randomUUID(),
      sourceId: p.id,
      amount: (Number(p.amount) - used).toFixed(2),
    },
    "/appointments/" + detail.value.appointment.id + "/refund",
  );
}
const actionNames = {
  arrive: ["登记到店", "Check in"],
  start: ["开始服务", "Start service"],
  finish: ["完成服务", "Finish service"],
  checkout: ["核对账单", "Confirm bill"],
  pay: ["登记收款", "Record payment"],
  cancel: ["取消预约", "Cancel booking"],
  "no-show": ["登记未到店", "Mark no-show"],
  reassign: ["更换员工", "Reassign"],
};
const labelAction = (a) => t(...actionNames[a]);
const actions = computed(() => {
  const o = detail.value?.appointment;
  if (!o) return [];
  let xs = [];
  if (o.status === "CONFIRMED") {
    if (can("appointment.manage")) xs = ["arrive", "no-show", "reassign"];
    if (can("appointment.manage") || isCustomer.value) xs.push("cancel");
  }
  if (o.status === "ARRIVED") {
    if (
      can("execute") &&
      catalog.value.staff.find((s) => s.id === o.staffId)?.accountId ===
        profile.value.id
    )
      xs.push("start");
    if (can("appointment.manage")) xs.push("reassign");
  }
  if (
    o.status === "IN_SERVICE" &&
    can("execute") &&
    catalog.value.staff.find((s) => s.id === o.staffId)?.accountId ===
      profile.value.id
  )
    xs.push("finish");
  if (o.status === "SERVED" && can("checkout"))
    xs.push(o.checkedOut ? "pay" : "checkout");
  return xs;
});
async function submit() {
  if (dialog.value.kind === "booking") return bookNow();
  busy.value = true;
  error.value = "";
  try {
    let body = { ...form.value };
    if (dialog.value.kind === "shifts")
      for (const key of [
        "startMinute",
        "endMinute",
        "breakStart",
        "breakEnd",
      ]) {
        const x = body[key];
        if (!x) body[key] = 0;
        else {
          const [h, m] = String(x).split(":").map(Number);
          body[key] = h * 60 + m;
        }
      }
    if (dialog.value.kind === "import") body = JSON.parse(body.json);
    await api(dialog.value.path, dialog.value.method, body);
    const id = detail.value?.appointment.id;
    const passwordChange = dialog.value.path === "/auth/password";
    dialog.value = null;
    if (passwordChange) {
      profile.value = null;
      resetCsrf();
      notice.value = t(
        "密码已更新，请重新登录",
        "Password updated. Please sign in.",
      );
      return;
    }
    await refresh();
    if (id) await openAppointment(id);
    notice.value = t("已保存", "Saved");
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function deleteRow(row) {
  const path =
    (masters.includes(page.value) ? "/master/" : "/admin/") +
    page.value +
    "/" +
    row.id;
  dialogForm("删除记录", "Delete record", [], {}, path, "DELETE");
}
function editRow(row) {
  if (adminPages.includes(page.value)) editAdmin(row);
  else editMaster(row);
}
function addRow() {
  if (page.value === "appointments") beginBooking();
  else editRow();
}
function retryMessage(row) {
  dialogForm(
    "重试邮件",
    "Retry email",
    [],
    {},
    "/messages/" + row.id + "/retry",
  );
}
function importClients() {
  dialogForm(
    "导入客户JSON",
    "Import client JSON",
    [
      F("json", "JSON记录数组", "JSON record array", "textarea", [], {
        maxlength: 500000,
      }),
    ],
    {
      json:
        '[{"name":"TEST 示例客户","departmentId":' +
        profile.value.departmentId +
        ',"enabled":true,"phone":"","email":"","emailConsent":false}]',
    },
    "/master/customers/import",
    "POST",
    "import",
  );
}
function ownProfile() {
  const c = catalog.value.customer;
  dialogForm(
    "联系方式",
    "Contact details",
    [
      F("phone", "联系电话", "Phone", "text", [], { required: false }),
      F("email", "邮箱", "Email", "email", [], {
        required: false,
        maxlength: 200,
      }),
      F(
        "emailConsent",
        "同意接收预约邮件",
        "Consent to booking emails",
        "checkbox",
      ),
    ],
    c,
    "/my-profile",
    "PUT",
  );
}
function passwordDialog() {
  dialogForm(
    "修改密码",
    "Change password",
    [
      F("oldPassword", "原密码", "Current password", "password"),
      F("newPassword", "新密码", "New password", "password", [], {
        minlength: 12,
        maxlength: 72,
      }),
    ],
    {},
    "/auth/password",
  );
}
function print() {
  window.print();
}
async function exportReport() {
  try {
    await downloadReport(reportFrom.value, reportTo.value);
  } catch (e) {
    fail(e);
  }
}
function about() {
  dialogForm("关于系统", "About SalonFlow", [], {}, "", "GET", "about");
}
const editable = computed(() =>
  adminPages.includes(page.value)
    ? can("admin")
    : page.value === "customers"
      ? can("customer.write")
      : ["shifts", "blocks"].includes(page.value)
        ? can("schedule")
        : masters.includes(page.value) && can("master.write"),
);
const canAdd = computed(() =>
  page.value === "appointments"
    ? can("appointment.manage") || isCustomer.value
    : editable.value &&
      !["permissions", "menus", "settings"].includes(page.value),
);
const columns = computed(() => {
  const common = {
    services: [
      ["code", "编码", "Code"],
      ["name", "项目", "Service"],
      ["categoryId", "分类", "Category"],
      ["price", "价格", "Price"],
      ["durationMinutes", "服务分钟", "Minutes"],
      ["bufferMinutes", "缓冲分钟", "Buffer"],
      ["resourceRequired", "需要资源", "Resource"],
      ["enabled", "启用", "Enabled"],
    ],
    categories: [
      ["name", "分类", "Category"],
      ["departmentId", "门店", "Salon"],
      ["enabled", "启用", "Enabled"],
    ],
    staff: [
      ["name", "姓名", "Name"],
      ["accountId", "登录账号", "Account"],
      ["services", "可做项目", "Services"],
      ["enabled", "启用", "Enabled"],
    ],
    resources: [
      ["code", "编码", "Code"],
      ["name", "座位或房间", "Seat / room"],
      ["enabled", "启用", "Enabled"],
    ],
    customers: [
      ["name", "姓名", "Name"],
      ["phone", "电话", "Phone"],
      ["email", "邮箱", "Email"],
      ["accountId", "自助账号", "Client account"],
      ["emailConsent", "邮件同意", "Email consent"],
      ["enabled", "启用", "Enabled"],
    ],
    shifts: [
      ["staffId", "员工", "Staff"],
      ["weekday", "星期", "Day"],
      ["startMinute", "上班", "Start"],
      ["endMinute", "下班", "End"],
      ["breakStart", "休息开始", "Break start"],
      ["breakEnd", "休息结束", "Break end"],
    ],
    blocks: [
      ["staffId", "员工", "Staff"],
      ["resourceId", "资源", "Resource"],
      ["startsAt", "开始", "Start"],
      ["endsAt", "结束", "End"],
      ["reason", "原因", "Reason"],
    ],
    payments: [
      ["appointmentId", "预约", "Appointment"],
      ["kind", "类型", "Type"],
      ["amount", "金额", "Amount"],
      ["method", "方式", "Method"],
      ["reference", "凭证号", "Reference"],
      ["createdAt", "时间", "Time"],
    ],
    messages: [
      ["appointmentId", "预约", "Appointment"],
      ["kind", "通知", "Message"],
      ["dueAt", "计划时间", "Due"],
      ["status", "状态", "Status"],
      ["attempts", "次数", "Attempts"],
      ["resultCode", "结果", "Result"],
    ],
    users: [
      ["username", "登录账号", "Username"],
      ["displayName", "姓名", "Name"],
      ["roleId", "角色", "Role"],
      ["departmentId", "门店", "Salon"],
      ["kind", "身份", "Kind"],
      ["enabled", "启用", "Enabled"],
    ],
    roles: [
      ["name", "角色", "Role"],
      ["scope", "范围", "Scope"],
      ["permissions", "操作权限", "Permissions"],
    ],
    departments: [["name", "门店部门", "Department"]],
    menus: [
      ["name", "中文名称", "Chinese label"],
      ["nameEn", "英文名称", "English label"],
      ["permissionCode", "权限", "Permission"],
      ["position", "顺序", "Position"],
      ["enabled", "启用", "Enabled"],
    ],
    permissions: [
      ["code", "编码", "Code"],
      ["name", "名称", "Name"],
    ],
    dictionaries: [
      ["type", "类型", "Type"],
      ["code", "编码", "Code"],
      ["name", "中文", "Chinese"],
      ["nameEn", "英文", "English"],
      ["enabled", "启用", "Enabled"],
    ],
    settings: [
      ["code", "参数", "Setting"],
      ["value", "值", "Value"],
    ],
    audit: [
      ["actor", "操作账号", "Actor"],
      ["action", "操作", "Action"],
      ["objectId", "对象", "Object"],
      ["createdAt", "时间", "Time"],
    ],
  };
  return common[page.value] || [];
});
function cell(row, key) {
  const value = row[key];
  if (typeof value === "boolean") return value ? t("是", "Yes") : t("否", "No");
  if (["price", "amount"].includes(key)) return fmt(value);
  if (key.endsWith("At")) return when(value);
  if (["startMinute", "endMinute", "breakStart", "breakEnd"].includes(key))
    return value === 0 && key.startsWith("break") ? "—" : minutes(value);
  if (key === "weekday")
    return t(
      ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"][value],
      ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][value],
    );
  const catalogs = {
    departmentId: "departments",
    roleId: "roles",
    categoryId: "categories",
    staffId: "staff",
    resourceId: "resources",
    accountId: "accounts",
  };
  if (catalogs[key])
    return (
      catalog.value[catalogs[key]]?.find((x) => x.id === value)?.name ||
      catalog.value[catalogs[key]]?.find((x) => x.id === value)?.displayName ||
      value ||
      "—"
    );
  if (key === "services")
    return value
      .map((id) => catalog.value.services?.find((s) => s.id === id)?.name || id)
      .join(" · ");
  if (Array.isArray(value)) return value.join(" · ");
  return label(value) || "—";
}
function shiftDate(n) {
  const x = new Date(date.value + "T12:00:00Z");
  x.setUTCDate(x.getUTCDate() + n);
  date.value = x.toISOString().slice(0, 10);
  pagination.value = 0;
  load();
}
const icon = (code) =>
  code === "dashboard" || code === "appointments" || code === "book"
    ? CalendarDays
    : code === "staff" || code === "customers" || code === "users"
      ? Users
      : code === "services"
        ? Scissors
        : code === "reports"
          ? ChartNoAxesColumn
          : code === "shifts" || code === "blocks"
            ? Clock3
            : Settings;
onMounted(async () => {
  try {
    publicCatalog.value = await api("/public/catalog");
    register.value.departmentId =
      publicCatalog.value.departments?.[0]?.id || "";
    profile.value = await api("/auth/me");
    await refresh();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!profile" class="entry">
    <div class="entry-top">
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><strong
          >SalonFlow</strong
        ></a
      ><button @click="toggleLanguage">
        {{ language === "zh" ? "EN" : "中文" }}
      </button>
    </div>
    <div class="entry-layout">
      <section class="salon-window">
        <p class="eyebrow">{{ t("门店预约", "SALON APPOINTMENTS") }}</p>
        <h1>{{ publicCatalog.companyName || "SalonFlow" }}</h1>
        <p class="entry-sub">
          {{
            t(
              "选择服务，预约你的时间。",
              "Choose your service. Reserve your time.",
            )
          }}
        </p>
        <div
          class="service-preview"
          v-for="s in publicCatalog.services"
          :key="s.id"
        >
          <Scissors :size="20" />
          <div>
            <strong>{{ s.name }}</strong
            ><span>{{ s.durationMinutes }} {{ t("分钟", "minutes") }}</span>
          </div>
          <b>{{ fmt(s.price) }}</b>
        </div>
        <p v-if="!publicCatalog.services?.length" class="quiet">
          {{
            t(
              "门店暂未发布可预约项目",
              "The salon has not published services yet",
            )
          }}
        </p>
        <footer>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >{{
              t(
                "知华科技 · 商业授权与部署咨询",
                "ZhuaTech · Licensing & deployment",
              )
            }}
            <ArrowUpRight :size="14"
          /></a>
        </footer>
      </section>
      <section class="entry-form">
        <div class="entry-form-title">
          <h2>
            {{
              signup
                ? t("创建预约账号", "Create an account")
                : t("登录", "Sign in")
            }}
          </h2>
          <p>
            {{
              signup
                ? t("已注册？", "Already registered?")
                : t("首次预约？", "New here?")
            }}
            <button
              v-if="publicCatalog.registration"
              class="text-button"
              :disabled="busy"
              @click="
                signup = !signup;
                error = '';
                notice = '';
              "
            >
              {{
                signup
                  ? t("去登录", "Sign in")
                  : t("创建账号", "Create account")
              }}
            </button>
          </p>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="success" role="status">{{ notice }}</p>
        <form v-if="!signup" @submit.prevent="signIn">
          <label
            >{{ t("账号", "Username")
            }}<input
              v-model="login.username"
              required
              autocomplete="username"
              maxlength="60" /></label
          ><label
            >{{ t("密码", "Password")
            }}<input
              v-model="login.password"
              type="password"
              required
              autocomplete="current-password"
              maxlength="72" /></label
          ><button class="primary" :disabled="busy">
            {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in") }}
          </button>
        </form>
        <form v-else @submit.prevent="signUp">
          <label
            >{{ t("姓名", "Name")
            }}<input v-model="register.name" required maxlength="120" /></label
          ><label
            >{{ t("预约门店", "Salon")
            }}<select v-model="register.departmentId" required>
              <option
                v-for="d in publicCatalog.departments"
                :key="d.id"
                :value="d.id"
              >
                {{ d.name }}
              </option>
            </select></label
          ><label
            >{{ t("登录账号", "Username")
            }}<input
              v-model="register.username"
              required
              minlength="3"
              maxlength="60"
              autocomplete="username" /></label
          ><label
            >{{
              t(
                "密码（12–72位，含大小写和数字）",
                "Password (12–72, uppercase, lowercase and digits)",
              )
            }}<input
              v-model="register.password"
              required
              type="password"
              minlength="12"
              maxlength="72"
              autocomplete="new-password" /></label
          ><label
            >{{ t("联系电话（选填）", "Phone (optional)")
            }}<input
              v-model="register.phone"
              maxlength="60"
              autocomplete="tel" /></label
          ><label
            >{{ t("邮箱（选填）", "Email (optional)")
            }}<input
              v-model="register.email"
              type="email"
              maxlength="200"
              autocomplete="email" /></label
          ><label class="checkbox"
            ><input v-model="register.emailConsent" type="checkbox" />{{
              t("同意接收预约邮件", "Consent to booking emails")
            }}</label
          ><button class="primary" :disabled="busy">
            {{ t("创建账号", "Create account") }}
          </button>
        </form>
        <p class="entry-license">
          {{
            t(
              "知华科技公开源码学习版 · 商用须书面授权",
              "ZhuaTech non-commercial source edition · Commercial use requires written permission",
            )
          }}
        </p>
      </section>
    </div>
  </div>
  <div v-else class="workspace">
    <aside :class="['sidebar', { open: sidebar }]">
      <a
        class="product"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>SalonFlow</strong
          ><span>{{ t("知华美业", "ZhuaTech Salon") }}</span>
        </div></a
      >
      <nav>
        <button
          v-for="m in profile.menus.filter(
            (m) => m.code !== 'book' || isCustomer,
          )"
          :key="m.code"
          :disabled="busy"
          :class="{ selected: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icon(m.code)" :size="17" />{{
            language === "en" ? m.nameEn : m.name
          }}
        </button>
      </nav>
      <footer>
        <span>{{
          t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries")
        }}</span
        ><span>zhuatech / zhuatech2</span
        ><button class="text-button" @click="about">
          {{ t("关于系统", "About SalonFlow") }}
        </button>
      </footer>
    </aside>
    <div v-if="sidebar" class="nav-shade" @click="sidebar = false"></div>
    <main>
      <header class="topbar">
        <button
          class="menu-button"
          :aria-label="t('菜单', 'Menu')"
          @click="sidebar = !sidebar"
        >
          <Menu :size="21" /></button
        ><span
          >{{ setting("companyName")
          }}<small>{{ zone }} · {{ currency }}</small></span
        >
        <div class="top-actions">
          <button @click="toggleLanguage">
            {{ language === "zh" ? "EN" : "中文" }}</button
          ><button v-if="isCustomer" @click="ownProfile">
            {{ profile.displayName }}</button
          ><button v-if="isCustomer" @click="passwordDialog">
            {{ t("改密码", "Password") }}</button
          ><button v-else @click="passwordDialog">
            {{ profile.displayName }}</button
          ><button
            :disabled="busy"
            :aria-label="t('退出', 'Sign out')"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <div class="content">
        <div class="page-heading">
          <div>
            <p class="eyebrow">
              {{
                isCustomer
                  ? t("我的预约", "MY APPOINTMENTS")
                  : t("门店工作台", "SALON OPERATIONS")
              }}
            </p>
            <h1>{{ detail ? t("预约详情", "Appointment detail") : title }}</h1>
          </div>
          <button v-if="detail" @click="detail = null">
            <ChevronLeft :size="16" />{{ t("返回", "Back") }}</button
          ><button
            v-else
            :disabled="loading"
            :aria-label="t('刷新', 'Refresh')"
            @click="refresh"
          >
            <RefreshCw :size="17" />
          </button>
        </div>
        <p v-if="error && !dialog" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="success" role="status">{{ notice }}</p>
        <template v-if="detail"
          ><div class="appointment-head">
            <div>
              <strong>{{ detail.appointment.number }}</strong
              ><span :class="['badge', detail.appointment.status]">{{
                label(detail.appointment.status)
              }}</span>
              <p>
                {{ detail.appointment.customerName }} ·
                {{ detail.appointment.serviceName }} ·
                {{ detail.appointment.staffName }}
              </p>
              <p>
                {{ when(detail.appointment.startsAt) }} —
                {{ when(detail.appointment.endsAt)
                }}<span v-if="detail.appointment.resourceName">
                  · {{ detail.appointment.resourceName }}</span
                >
              </p>
            </div>
            <div class="action-row">
              <button @click="print">
                <Printer :size="16" />{{ t("打印", "Print") }}</button
              ><button
                v-if="
                  detail.appointment.status === 'CONFIRMED' &&
                  (can('appointment.manage') || isCustomer)
                "
                @click="reschedule(detail.appointment)"
              >
                {{ t("改期", "Reschedule") }}</button
              ><button
                v-for="a in actions"
                :key="a"
                :class="a === 'cancel' ? 'danger' : 'primary'"
                @click="stateAction(a)"
              >
                {{ labelAction(a) }}
              </button>
            </div>
          </div>
          <div class="detail-grid">
            <section class="panel">
              <h2>{{ t("服务记录", "Service record") }}</h2>
              <dl>
                <dt>{{ t("预约备注", "Booking note") }}</dt>
                <dd>{{ detail.appointment.note || "—" }}</dd>
                <dt>{{ t("实际开始", "Actual start") }}</dt>
                <dd>{{ when(detail.appointment.serviceStartedAt) }}</dd>
                <dt>{{ t("实际完成", "Actual finish") }}</dt>
                <dd>{{ when(detail.appointment.serviceFinishedAt) }}</dd>
                <dt>{{ t("服务完成记录", "Completion note") }}</dt>
                <dd>{{ detail.appointment.serviceNote || "—" }}</dd>
              </dl>
            </section>
            <section class="panel bill">
              <h2>{{ t("账单", "Bill") }}</h2>
              <dl>
                <dt>{{ t("项目原价", "Service price") }}</dt>
                <dd>{{ fmt(detail.appointment.price) }}</dd>
                <dt>{{ t("折扣", "Discount") }}</dt>
                <dd>−{{ fmt(detail.appointment.discount) }}</dd>
                <dt>{{ t("应收", "Total due") }}</dt>
                <dd class="bill-total">{{ fmt(detail.appointment.total) }}</dd>
                <dt>{{ t("实际收款", "Received") }}</dt>
                <dd>{{ fmt(detail.appointment.paid) }}</dd>
                <dt>{{ t("已退金额", "Refunded") }}</dt>
                <dd>{{ fmt(detail.appointment.refunded) }}</dd>
                <dt>{{ t("未收款", "Outstanding") }}</dt>
                <dd>
                  {{
                    fmt(
                      Number(detail.appointment.total) -
                        Number(detail.appointment.paid),
                    )
                  }}
                </dd>
              </dl>
            </section>
          </div>
          <section class="panel">
            <h2>{{ t("收退款记录", "Payments & refunds") }}</h2>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("类型", "Type") }}</th>
                    <th>{{ t("金额", "Amount") }}</th>
                    <th>{{ t("方式", "Method") }}</th>
                    <th>{{ t("凭证号", "Reference") }}</th>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in detail.payments" :key="p.id">
                    <td>{{ label(p.kind) }}</td>
                    <td>{{ fmt(p.amount) }}</td>
                    <td>{{ p.method }}</td>
                    <td>
                      {{ p.reference }}<small>{{ p.reason }}</small>
                    </td>
                    <td>{{ when(p.createdAt) }}</td>
                    <td>
                      <button
                        v-if="
                          p.kind === 'PAYMENT' &&
                          detail.appointment.status === 'COMPLETED' &&
                          can('refund')
                        "
                        @click="refundPayment(p)"
                      >
                        {{ t("退款", "Refund") }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!detail.payments.length" class="empty">
              {{ t("尚无收退款记录", "No payment records") }}
            </p>
          </section>
          <section class="panel">
            <h2>{{ t("预约履历", "Appointment history") }}</h2>
            <ol class="history">
              <li v-for="e in detail.events" :key="e.id">
                <span>{{ when(e.createdAt) }}</span
                ><strong>{{ label(e.kind) }}</strong>
                <p>{{ e.note || "—" }}</p>
              </li>
            </ol>
          </section></template
        >
        <template v-else-if="page === 'dashboard'"
          ><div class="overview-counts">
            <div
              v-for="key in ['CONFIRMED', 'ARRIVED', 'IN_SERVICE', 'SERVED']"
              :key="key"
            >
              <span>{{ label(key) }}</span
              ><strong>{{ activeCount(key) }}</strong>
            </div>
          </div>
          <section class="panel">
            <div class="section-head">
              <h2>
                {{
                  isCustomer
                    ? t("即将到来的预约", "Upcoming appointments")
                    : t("今日预约", "Today’s appointments")
                }}
                <small>{{ overview.date }}</small>
              </h2>
              <button
                v-if="can('appointment.manage') || isCustomer"
                class="primary"
                @click="beginBooking"
              >
                <Plus :size="16" />{{ t("新增预约", "New appointment") }}
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("客户", "Client") }}</th>
                    <th>{{ t("项目", "Service") }}</th>
                    <th>{{ t("员工", "Staff") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="o in isCustomer ? overview.upcoming : overview.today"
                    :key="o.id"
                  >
                    <td>{{ when(o.startsAt) }}</td>
                    <td>{{ o.customerName }}</td>
                    <td>{{ o.serviceName }}</td>
                    <td>{{ o.staffName }}</td>
                    <td>
                      <span :class="['badge', o.status]">{{
                        label(o.status)
                      }}</span>
                    </td>
                    <td>
                      <button @click="openAppointment(o.id)">
                        {{ t("打开", "Open") }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p
              v-if="!(isCustomer ? overview.upcoming : overview.today).length"
              class="empty"
            >
              {{ t("暂无预约", "No appointments") }}
            </p>
          </section></template
        >
        <template v-else-if="page === 'book'"
          ><section class="panel booking-panel">
            <h2>{{ t("选择服务与时间", "Choose service and time") }}</h2>
            <form @submit.prevent="bookNow">
              <div class="form-grid">
                <label
                  >{{ t("服务项目", "Service")
                  }}<select
                    v-model="booking.serviceId"
                    required
                    @change="
                      slotQuery++;
                      slotLoading = false;
                      booking.staffId = '';
                      booking.resourceId = '';
                      available = [];
                      booking.startsAt = '';
                    "
                  >
                    <option value="">{{ t("请选择", "Select") }}</option>
                    <option
                      v-for="s in catalog.services.filter((s) => s.enabled)"
                      :key="s.id"
                      :value="s.id"
                    >
                      {{ s.name }} · {{ s.durationMinutes }}min ·
                      {{ fmt(s.price) }}
                    </option>
                  </select></label
                ><label
                  >{{ t("服务员工", "Stylist")
                  }}<select
                    v-model="booking.staffId"
                    required
                    @change="fetchSlots"
                  >
                    <option value="">{{ t("请选择", "Select") }}</option>
                    <option
                      v-for="s in eligibleStaff"
                      :key="s.id"
                      :value="s.id"
                    >
                      {{ s.name }}
                    </option>
                  </select></label
                ><label
                  v-if="
                    selectedService?.resourceRequired ||
                    eligibleResources.length
                  "
                  >{{ t("座位或房间", "Seat / room")
                  }}<select
                    v-model="booking.resourceId"
                    :required="selectedService?.resourceRequired"
                    @change="fetchSlots"
                  >
                    <option value="">{{ t("不指定", "None") }}</option>
                    <option
                      v-for="r in eligibleResources"
                      :key="r.id"
                      :value="r.id"
                    >
                      {{ r.name }}
                    </option>
                  </select></label
                ><label
                  >{{ t("日期", "Date")
                  }}<input
                    v-model="booking.date"
                    type="date"
                    required
                    @change="fetchSlots"
                /></label>
              </div>
              <div class="slot-heading">
                <h3>{{ t("可预约时间", "Available times") }}</h3>
                <button
                  type="button"
                  :disabled="slotLoading"
                  @click="fetchSlots"
                >
                  <RefreshCw :size="14" />{{ t("查询", "Find slots") }}
                </button>
              </div>
              <div class="slots">
                <button
                  v-for="s in available"
                  :key="s.startsAt"
                  type="button"
                  :class="{ selected: booking.startsAt === s.startsAt }"
                  @click="booking.startsAt = s.startsAt"
                >
                  {{ s.label }}
                </button>
              </div>
              <p v-if="!available.length" class="empty">
                {{
                  slotLoading
                    ? t("正在查询…", "Finding times…")
                    : t(
                        "请选择服务、员工和日期后查询",
                        "Select a service, stylist and date",
                      )
                }}
              </p>
              <label
                >{{ t("预约备注（选填）", "Note (optional)")
                }}<textarea
                  v-model="booking.note"
                  maxlength="1000"
                  rows="2"
                ></textarea></label
              ><button class="primary" :disabled="busy || !booking.startsAt">
                {{ t("确认预约", "Confirm booking") }}
              </button>
            </form>
          </section></template
        >
        <template v-else-if="page === 'reports'"
          ><section class="panel">
            <form class="toolbar" @submit.prevent="load">
              <label
                >{{ t("开始日期", "From")
                }}<input v-model="reportFrom" type="date" required /></label
              ><label
                >{{ t("结束日期", "To")
                }}<input v-model="reportTo" type="date" required /></label
              ><button class="primary">{{ t("查询", "Apply") }}</button
              ><button type="button" @click="exportReport">
                <Download :size="15" />CSV
              </button>
            </form>
            <div class="overview-counts">
              <div
                v-for="[key, zh, en] in [
                  ['receipts', '实收款', 'Receipts'],
                  ['refunds', '退款', 'Refunds'],
                  ['netReceipts', '净收款', 'Net receipts'],
                  ['serviceSales', '完结服务销售', 'Completed sales'],
                ]"
                :key="key"
              >
                <span>{{ t(zh, en) }}</span
                ><strong>{{ fmt(report[key]) }}</strong>
              </div>
            </div>
            <p class="quiet">
              {{ t("完结服务", "Completed services") }}
              {{ report.completed || 0 }} ·
              {{ t("当前未收款", "Current outstanding") }}
              {{ fmt(report.outstanding) }}
            </p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("预约ID", "Appointment ID") }}</th>
                    <th>{{ t("类型", "Type") }}</th>
                    <th>{{ t("金额", "Amount") }}</th>
                    <th>{{ t("凭证号", "Reference") }}</th>
                    <th>{{ t("实际发生时间", "Recorded at") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in report.items" :key="p.id">
                    <td>{{ p.appointmentId }}</td>
                    <td>{{ label(p.kind) }}</td>
                    <td>{{ fmt(p.amount) }}</td>
                    <td>{{ p.reference }}</td>
                    <td>{{ when(p.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else
          ><section class="panel listing">
            <form
              class="toolbar"
              @submit.prevent="
                pagination = 0;
                load();
              "
            >
              <div class="search-box">
                <Search :size="16" /><input
                  v-model="search"
                  :placeholder="t('搜索记录', 'Search records')"
                  :aria-label="t('搜索', 'Search')"
                  maxlength="200"
                />
              </div>
              <select
                v-if="['appointments', 'messages', 'payments'].includes(page)"
                v-model="status"
                :aria-label="t('状态', 'Status')"
                @change="
                  pagination = 0;
                  load();
                "
              >
                <option value="">{{ t("全部状态", "All statuses") }}</option>
                <option
                  v-for="key in page === 'appointments'
                    ? [
                        'CONFIRMED',
                        'ARRIVED',
                        'IN_SERVICE',
                        'SERVED',
                        'COMPLETED',
                        'CANCELLED',
                        'NO_SHOW',
                      ]
                    : page === 'payments'
                      ? ['PAYMENT', 'REFUND']
                      : [
                          'QUEUED',
                          'SENT',
                          'FAILED',
                          'NOT_CONFIGURED',
                          'CANCELLED',
                          'SKIPPED',
                        ]"
                  :key="key"
                  :value="key"
                >
                  {{ label(key) }}
                </option></select
              ><button>{{ t("搜索", "Search") }}</button
              ><span class="toolbar-spacer"></span
              ><button
                v-if="page === 'customers' && can('customer.write')"
                type="button"
                @click="importClients"
              >
                {{ t("导入", "Import") }}</button
              ><button
                v-if="canAdd"
                class="primary"
                type="button"
                @click="addRow"
              >
                <Plus :size="16" />{{ t("新增", "Add") }}
              </button>
            </form>
            <div
              v-if="page === 'appointments' && !isCustomer"
              class="calendar-toolbar"
            >
              <div>
                <button
                  :aria-label="t('前一天', 'Previous day')"
                  @click="shiftDate(-1)"
                >
                  <ChevronLeft :size="16" /></button
                ><input
                  v-model="date"
                  type="date"
                  :aria-label="t('日历日期', 'Calendar date')"
                  @change="
                    pagination = 0;
                    load();
                  "
                /><button
                  :aria-label="t('后一天', 'Next day')"
                  @click="shiftDate(1)"
                >
                  <ChevronRight :size="16" />
                </button>
              </div>
              <div class="segmented">
                <button
                  :class="{ selected: viewMode === 'calendar' }"
                  @click="
                    viewMode = 'calendar';
                    pagination = 0;
                    load();
                  "
                >
                  {{ t("日历", "Calendar") }}</button
                ><button
                  :class="{ selected: viewMode === 'list' }"
                  @click="
                    viewMode = 'list';
                    pagination = 0;
                    load();
                  "
                >
                  {{ t("列表", "List") }}
                </button>
              </div>
            </div>
            <div
              v-if="
                page === 'appointments' &&
                viewMode === 'calendar' &&
                !isCustomer
              "
              class="calendar-scroll"
            >
              <div
                class="calendar-board"
                :style="{ '--columns': Math.max(calendarStaff.length, 1) }"
              >
                <section
                  v-for="s in calendarStaff"
                  :key="s.id"
                  class="staff-column"
                >
                  <header>
                    <strong>{{ s.name }}</strong
                    ><span
                      >{{ calendarJobs(s.id).length }}
                      {{ t("个预约", "bookings") }}</span
                    >
                  </header>
                  <div class="day-agenda">
                    <button
                      v-for="o in calendarJobs(s.id)"
                      :key="o.id"
                      :class="['booking-card', o.status]"
                      @click="openAppointment(o.id)"
                    >
                      <time>{{ when(o.startsAt) }} — {{ when(o.endsAt) }}</time
                      ><strong>{{ o.customerName }}</strong
                      ><span>{{ o.serviceName }}</span
                      ><small>{{ o.resourceName || label(o.status) }}</small
                      ><span :class="['badge', o.status]">{{
                        label(o.status)
                      }}</span>
                    </button>
                    <p v-if="!calendarJobs(s.id).length" class="empty">
                      {{ t("暂无预约", "No bookings") }}
                    </p>
                  </div>
                </section>
                <p v-if="!calendarStaff.length" class="empty">
                  {{
                    t("请先配置员工与排班", "Configure staff and shifts first")
                  }}
                </p>
              </div>
            </div>
            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr v-if="page === 'appointments'">
                    <th>
                      <button
                        class="sort-button"
                        @click="
                          sort = 'startsAt';
                          descending = !descending;
                          load();
                        "
                      >
                        {{ t("预约时间", "Time") }} ↕
                      </button>
                    </th>
                    <th>{{ t("客户与单号", "Client / booking") }}</th>
                    <th>{{ t("服务项目", "Service") }}</th>
                    <th>{{ t("员工", "Staff") }}</th>
                    <th>{{ t("金额", "Price") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                  <tr v-else>
                    <th v-for="c in columns" :key="c[0]">
                      {{ t(c[1], c[2]) }}
                    </th>
                    <th v-if="editable || page === 'messages'">
                      {{ t("操作", "Actions") }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <template v-if="page === 'appointments'"
                    ><tr v-for="o in rows" :key="o.id">
                      <td>
                        {{ when(o.startsAt)
                        }}<small>{{ o.durationMinutes }}min</small>
                      </td>
                      <td>
                        {{ o.customerName }}<small>{{ o.number }}</small>
                      </td>
                      <td>{{ o.serviceName }}</td>
                      <td>{{ o.staffName }}</td>
                      <td>{{ fmt(o.price) }}</td>
                      <td>
                        <span :class="['badge', o.status]">{{
                          label(o.status)
                        }}</span>
                      </td>
                      <td>
                        <button @click="openAppointment(o.id)">
                          {{ t("打开", "Open") }}
                        </button>
                      </td>
                    </tr></template
                  ><template v-else
                    ><tr v-for="r in rows" :key="r.id">
                      <td v-for="c in columns" :key="c[0]">
                        {{ cell(r, c[0]) }}
                      </td>
                      <td v-if="editable">
                        <div class="row-actions">
                          <button @click="editRow(r)">
                            {{ t("编辑", "Edit") }}</button
                          ><button
                            v-if="
                              !['permissions', 'menus', 'settings'].includes(
                                page,
                              )
                            "
                            class="danger text-button"
                            @click="deleteRow(r)"
                          >
                            {{ t("删除", "Delete") }}
                          </button>
                        </div>
                      </td>
                      <td v-else-if="page === 'messages'">
                        <button
                          v-if="['FAILED', 'NOT_CONFIGURED'].includes(r.status)"
                          @click="retryMessage(r)"
                        >
                          {{ t("重试", "Retry") }}
                        </button>
                      </td>
                    </tr></template
                  >
                </tbody>
              </table>
              <p v-if="!rows.length" class="empty">
                {{
                  loading
                    ? t("正在加载…", "Loading…")
                    : t("暂无记录", "No records")
                }}
              </p>
            </div>
            <footer class="pagination">
              <span
                >{{ total }} {{ t("条记录", "records")
                }}<span
                  v-if="
                    page === 'appointments' &&
                    viewMode === 'calendar' &&
                    !isCustomer &&
                    total > 100
                  "
                >
                  ·
                  {{
                    t(
                      "当前显示100条，请使用列表查看全部",
                      "Showing 100; use list view for all",
                    )
                  }}</span
                ></span
              >
              <div
                v-if="
                  page !== 'appointments' || viewMode === 'list' || isCustomer
                "
              >
                <button
                  :disabled="pagination === 0"
                  :aria-label="t('上一页', 'Previous page')"
                  @click="
                    pagination--;
                    load();
                  "
                >
                  <ChevronLeft :size="16" /></button
                ><span>{{ pagination + 1 }}</span
                ><button
                  :disabled="(pagination + 1) * 20 >= total"
                  :aria-label="t('下一页', 'Next page')"
                  @click="
                    pagination++;
                    load();
                  "
                >
                  <ChevronRight :size="16" />
                </button>
              </div>
            </footer></section
        ></template>
      </div>
    </main>
  </div>
  <div v-if="dialog" class="modal-mask" @click.self="!busy && (dialog = null)">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t(dialog.zh, dialog.en)"
    >
      <header>
        <h2>{{ t(dialog.zh, dialog.en) }}</h2>
        <button
          :disabled="busy"
          :aria-label="t('关闭', 'Close')"
          @click="dialog = null"
        >
          <X :size="19" />
        </button>
      </header>
      <div v-if="dialog.kind === 'about'" class="dialog-body about">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <h3>SalonFlow 1.0.0</h3>
        <p>知华科技（上海如静知华信息科技有限公司）</p>
        <p>
          {{
            t(
              "公开源码学习版 · 未经书面授权不得商用",
              "Non-commercial source edition. Commercial use requires written permission.",
            )
          }}
        </p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >www.zhuatech.cn</a
        >
        <p>
          {{
            t(
              "商业授权、部署、定制与系统集成",
              "Licensing, deployment, customization & integration",
            )
          }}
        </p>
        <p>WeChat: zhuatech / zhuatech2</p>
      </div>
      <form v-else @submit.prevent="submit">
        <div class="dialog-body">
          <p v-if="error" class="error" role="alert">{{ error }}</p>
          <template v-if="dialog.kind === 'booking'"
            ><div class="form-grid">
              <label
                >{{ t("服务项目", "Service")
                }}<select
                  v-model="booking.serviceId"
                  required
                  :disabled="!!dialog.appointment"
                  @change="
                    slotQuery++;
                    slotLoading = false;
                    booking.staffId = '';
                    booking.resourceId = '';
                    available = [];
                    booking.startsAt = '';
                  "
                >
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="s in catalog.services.filter(
                      (s) =>
                        s.enabled || s.id === dialog.appointment?.serviceId,
                    )"
                    :key="s.id"
                    :value="s.id"
                  >
                    {{ s.name }} ·
                    {{
                      dialog.appointment?.durationMinutes ?? s.durationMinutes
                    }}min ·
                    {{ fmt(dialog.appointment?.price ?? s.price) }}
                  </option>
                </select></label
              ><label v-if="!isCustomer"
                >{{ t("客户", "Client")
                }}<select
                  v-model="booking.customerId"
                  required
                  :disabled="!!dialog.appointment"
                >
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="c in catalog.customers?.filter((c) => c.enabled)"
                    :key="c.id"
                    :value="c.id"
                  >
                    {{ c.name }}
                  </option>
                </select></label
              ><label
                >{{ t("服务员工", "Stylist")
                }}<select
                  v-model="booking.staffId"
                  required
                  @change="fetchSlots"
                >
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option v-for="s in eligibleStaff" :key="s.id" :value="s.id">
                    {{ s.name }}
                  </option>
                </select></label
              ><label
                v-if="
                  selectedService?.resourceRequired || eligibleResources.length
                "
                >{{ t("座位或房间", "Seat / room")
                }}<select
                  v-model="booking.resourceId"
                  :required="selectedService?.resourceRequired"
                  @change="fetchSlots"
                >
                  <option value="">{{ t("不指定", "None") }}</option>
                  <option
                    v-for="r in eligibleResources"
                    :key="r.id"
                    :value="r.id"
                  >
                    {{ r.name }}
                  </option>
                </select></label
              ><label
                >{{ t("日期", "Date")
                }}<input
                  v-model="booking.date"
                  type="date"
                  required
                  @change="fetchSlots"
              /></label>
            </div>
            <div class="slot-heading">
              <h3>{{ t("可预约时间", "Available times") }}</h3>
              <button type="button" :disabled="slotLoading" @click="fetchSlots">
                {{ t("查询", "Find slots") }}
              </button>
            </div>
            <div class="slots">
              <button
                v-for="s in available"
                :key="s.startsAt"
                type="button"
                :class="{ selected: booking.startsAt === s.startsAt }"
                @click="booking.startsAt = s.startsAt"
              >
                {{ s.label }}
              </button>
            </div>
            <p v-if="!available.length" class="empty">
              {{
                slotLoading
                  ? t("正在查询…", "Finding times…")
                  : t(
                      "请选择服务、员工和日期后查询",
                      "Select a service, stylist and date",
                    )
              }}
            </p>
            <label
              >{{ t("备注（选填）", "Note (optional)")
              }}<textarea
                v-model="booking.note"
                maxlength="1000"
                rows="2"
              ></textarea></label
          ></template>
          <div v-else class="form-grid">
            <label
              v-for="f in dialog.fields"
              :key="f.name"
              :class="{
                full: ['textarea', 'checks'].includes(f.type),
                checkbox: f.type === 'checkbox',
              }"
              ><template v-if="f.type === 'checkbox'"
                ><input v-model="form[f.name]" type="checkbox" />{{
                  t(f.zh, f.en)
                }}</template
              ><template v-else
                ><span>{{ t(f.zh, f.en) }}</span
                ><select
                  v-if="f.type === 'select'"
                  v-model="form[f.name]"
                  :required="f.required !== false"
                >
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="o in f.options"
                    :key="o.value"
                    :value="o.value"
                  >
                    {{ o.label }}
                  </option></select
                ><textarea
                  v-else-if="f.type === 'textarea'"
                  v-model="form[f.name]"
                  :required="f.required !== false"
                  :maxlength="f.maxlength || 1000"
                  rows="4"
                ></textarea>
                <div v-else-if="f.type === 'checks'" class="check-list">
                  <label v-for="o in f.options" :key="o.value" class="checkbox"
                    ><input
                      v-model="form[f.name]"
                      :value="o.value"
                      type="checkbox"
                    />{{ o.label }}</label
                  >
                </div>
                <input
                  v-else
                  v-model="form[f.name]"
                  :type="f.type"
                  :required="f.required !== false"
                  :pattern="f.pattern"
                  :min="f.min"
                  :max="f.max"
                  :step="f.step"
                  :minlength="f.minlength"
                  :maxlength="f.maxlength || 120"
                  :autocomplete="
                    f.type === 'password' ? 'new-password' : 'off'
                  " /></template
            ></label>
          </div>
          <p
            v-if="!dialog.fields.length && dialog.kind !== 'booking'"
            class="quiet"
          >
            {{ t("确认执行此操作？", "Confirm this action?") }}
          </p>
        </div>
        <footer>
          <button type="button" :disabled="busy" @click="dialog = null">
            {{ t("取消", "Cancel") }}</button
          ><button
            class="primary"
            :disabled="busy || (dialog.kind === 'booking' && !booking.startsAt)"
          >
            {{ busy ? t("正在保存…", "Saving…") : t("确认", "Confirm") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
