import re

with open(r"C:\Users\Ritvik\OneDrive\Desktop\Vehicle rental project\backend\src\test\java\com\vehicle\rental\service\PaymentServiceTest.java", "r") as f:
    content = f.read()

# Pattern for order_rzp1
content = re.sub(
    r'(when\(paymentRepository\.findByRazorpayOrderIdWithPessimisticLock\("order_rzp1"\)\)\.thenReturn\(Optional\.of\(payment\)\);)',
    r'when(paymentRepository.findByRazorpayOrderId("order_rzp1")).thenReturn(Optional.of(payment));\n        \1',
    content
)

# For order_missing
content = re.sub(
    r'(when\(paymentRepository\.findByRazorpayOrderIdWithPessimisticLock\("order_missing"\)\)\.thenReturn\(Optional\.empty\(\)\);)',
    r'when(paymentRepository.findByRazorpayOrderId("order_missing")).thenReturn(Optional.empty());\n        \1',
    content
)

# For order_other
content = re.sub(
    r'(when\(paymentRepository\.findByRazorpayOrderIdWithPessimisticLock\("order_other"\)\)\.thenReturn\(Optional\.of\(payment\)\);)',
    r'when(paymentRepository.findByRazorpayOrderId("order_other")).thenReturn(Optional.of(payment));\n        \1',
    content
)

with open(r"C:\Users\Ritvik\OneDrive\Desktop\Vehicle rental project\backend\src\test\java\com\vehicle\rental\service\PaymentServiceTest.java", "w") as f:
    f.write(content)

print("Done")