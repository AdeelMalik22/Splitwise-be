from rest_framework import serializers

from core.models import Activity


class ActivitySerializer(serializers.ModelSerializer):
    actor_username = serializers.CharField(source='actor.username', read_only=True)
    actor_name = serializers.CharField(source='actor.name', read_only=True)

    class Meta:
        model = Activity
        fields = ('id', 'actor', 'actor_username', 'actor_name', 'action', 'entity_type', 'entity_id',
                  'group_id', 'metadata', 'created_at')
        read_only_fields = fields
