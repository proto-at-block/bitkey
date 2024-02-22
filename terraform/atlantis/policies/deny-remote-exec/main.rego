package main

remote_exec_provisioners[resource] {
	[path, value] := walk(input)

	resource := value.resources[_]
	provisioner := resource.provisioners[_]
	provisioner.type == "remote-exec"
}

deny[msg] {
	count(remote_exec_provisioners) > 0
	msg := "remote-exec provisioners cannot be used"
}
