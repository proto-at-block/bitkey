package main

import rego.v1

remote_exec_provisioners contains resource if {
	[path, value] := walk(input)

	resource := value.resources[_]
	provisioner := resource.provisioners[_]
	provisioner.type == "remote-exec"
}

deny contains msg if {
	count(remote_exec_provisioners) > 0
	msg := "remote-exec provisioners cannot be used"
}
