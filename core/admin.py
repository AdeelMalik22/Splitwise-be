from django.contrib import admin

from core.models import UserGroup, Group, Expense


# Register your models here.

@admin.register(UserGroup)
class UserGroupAdmin(admin.ModelAdmin):
    list_display = ["id","user_id","group_id_id"]


@admin.register(Group)
class GroupAdmin(admin.ModelAdmin):
    list_display = ["id","name","description"]

@admin.register(Expense)
class ExpenseAdmin(admin.ModelAdmin):
    list_display = ["name", "description", "amount", "group_id_id", "created_at"]
