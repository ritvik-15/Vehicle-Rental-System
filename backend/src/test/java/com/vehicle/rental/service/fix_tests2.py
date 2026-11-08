import re

with open(r"C:\Users\Ritvik\OneDrive\Desktop\Vehicle rental project\backend\src\test\java\com\vehicle\rental\service\PaymentServiceTest.java", "r") as f:
    content = f.read()

# Replace all occurrences of findByRazorpayOrderIdWithPessimisticLock with the proper mock setup
# Pattern: findByRazorpayOrderIdWithPessimisticLock("order_rzp1") without preceding findByRazorpayOrderId

# First, handle the case where there's already a findByRazorpayOrderId mock before the pessimistic lock call
# We need to ensure every findByRazorpayOrderIdWithPessimisticLock("order_rzp1") has a preceding findByRazorpayOrderId mock

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

# Also handle cases where there's already a findByRazorpayOrderId but it's after the pessimistic lock call
# (should not happen in well-formed tests, but just in case)

with open(r"C:\Users\Ritvik\OneDrive\Desktop\Vehicle rental project\backend\src\test\java\com\vehicle\rental\service\PaymentServiceTest.java", "w") as f:
    f.write(content)

print("Done")